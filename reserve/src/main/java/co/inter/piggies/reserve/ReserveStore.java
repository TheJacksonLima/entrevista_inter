package co.inter.piggies.reserve;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência. Cada método é uma operação atômica no banco; a transação que as agrupa
 * é aberta pelo {@link ReserveService}.
 */
public interface ReserveStore {

    /**
     * Insere a reserva PENDING; não faz nada se o paymentId já existir
     * ({@code ON CONFLICT DO NOTHING}). Em caso de corrida, bloqueia até a outra transação terminar.
     *
     * @return true se inseriu; false se já existia
     */
    boolean insertPending(UUID paymentId, String clientId, long amount);

    /** {@code available -= v, reserved += v WHERE available >= v}. false => saldo insuficiente/conta inexistente. */
    boolean moveAvailableToReserved(String clientId, long amount);

    Optional<Reservation> find(UUID paymentId);

    /** Transição condicional {@code WHERE status = from}. false => outro request já mudou o estado. */
    boolean transition(UUID paymentId, ReserveStatus from, ReserveStatus to);

    /** Confirmação: {@code reserved -= v}. */
    boolean debitReserved(String clientId, long amount);

    /** Liberação: {@code reserved -= v, available += v}. */
    boolean returnReservedToAvailable(String clientId, long amount);

    /** Cria a conta se não existir (nunca sobrescreve saldo existente). */
    void createAccountIfAbsent(String clientId, long available);
}
