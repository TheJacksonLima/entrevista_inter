package co.inter.piggies.reserve;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Casos de uso de reserva. Cada método público roda em UMA transação; exceções de negócio
 * (RuntimeException) provocam rollback — p.ex. o INSERT da reserva é desfeito quando o saldo é insuficiente.
 */
@Singleton
public class ReserveService {

    private static final int MAX_ATTEMPTS = 3;

    private final ReserveStore store;

    public ReserveService(ReserveStore store) {
        this.store = store;
    }

    /** Resultado de {@link #reserve}: a reserva e se ela foi criada agora (201) ou já existia (200). */
    public record ReserveResult(Reservation reservation, boolean created) {
    }

    @Transactional
    public ReserveResult reserve(UUID paymentId, String clientId, long amount) {
        // 1) Reivindica o paymentId (idempotência/corrida resolvidas pelo PK no banco).
        if (!store.insertPending(paymentId, clientId, amount)) {
            return new ReserveResult(get(paymentId), false);
        }
        // 2) Separa o saldo de forma atômica. 0 linhas => insuficiente; a exceção desfaz o INSERT.
        if (!store.moveAvailableToReserved(clientId, amount)) {
            throw new ReserveException.InsufficientBalance(clientId, amount);
        }
        return new ReserveResult(new Reservation(paymentId, clientId, amount, ReserveStatus.PENDING), true);
    }

    @Transactional
    public Reservation confirm(UUID paymentId) {
        return transition(paymentId, "confirmar", ReserveStatus.CONFIRMED);
    }

    @Transactional
    public Reservation release(UUID paymentId) {
        return transition(paymentId, "liberar", ReserveStatus.RELEASED);
    }

    @Transactional
    public Reservation get(UUID paymentId) {
        return store.find(paymentId).orElseThrow(() -> new ReserveException.NotFound(paymentId));
    }

    private Reservation transition(UUID paymentId, String verb, ReserveStatus target) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Reservation current = get(paymentId);
            ReserveRules.Action action = target == ReserveStatus.CONFIRMED
                    ? ReserveRules.confirm(current.status())
                    : ReserveRules.release(current.status());
            switch (action) {
                case NOOP:
                    return current;
                case CONFLICT:
                    throw new ReserveException.InvalidState(paymentId, current.status(), verb);
                case APPLY:
                    if (store.transition(paymentId, ReserveStatus.PENDING, target)) {
                        boolean moved = target == ReserveStatus.CONFIRMED
                                ? store.debitReserved(current.clientId(), current.amount())
                                : store.returnReservedToAvailable(current.clientId(), current.amount());
                        if (!moved) {
                            // Invariante violada (reserva sem saldo reservado): aborta e faz rollback.
                            throw new IllegalStateException("Conta inconsistente para a reserva " + paymentId);
                        }
                        return current.withStatus(target);
                    }
                    // Outro request mudou o estado entre a leitura e o UPDATE: relê e reavalia.
                    break;
            }
        }
        throw new IllegalStateException("Não foi possível " + verb + " a reserva " + paymentId);
    }
}
