package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.domain.Payment;
import io.micronaut.data.exceptions.DataAccessException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Registra a intenção de pagamento (PENDING). {@code repository.save} commita na própria chamada,
 * portanto quando {@link #register} retorna o registro já está durável; só então a borda aciona o orquestrador.
 */
@Singleton
public class PaymentService {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentService.class);

    /** created = false quando a Idempotency-Key já existia (nada novo a orquestrar). */
    public record Registration(Payment payment, boolean created) {
    }

    private final PaymentRepository repository;
    private final Clock clock;

    @Inject
    public PaymentService(PaymentRepository repository) {
        this(repository, Clock.systemUTC());
    }

    PaymentService(PaymentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Registration register(String clientId, String merchantId, long amount, String idempotencyKey) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey;
        if (key != null) {
            Optional<Payment> existing = repository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return new Registration(existing.get(), false);
            }
        }
        Payment payment = Payment.pending(UUID.randomUUID(), clientId, merchantId, amount, key, clock.instant());
        try {
            return new Registration(repository.save(payment), true);
        } catch (DataAccessException e) {
            // Corrida: outra requisição com a mesma chave commitou primeiro (constraint UNIQUE).
            if (key != null) {
                Optional<Payment> winner = repository.findByIdempotencyKey(key);
                if (winner.isPresent()) {
                    LOG.info("Idempotency-Key duplicada em corrida; devolvendo pagamento {}", winner.get().getId());
                    return new Registration(winner.get(), false);
                }
            }
            throw e;
        }
    }

    public Optional<Payment> find(UUID paymentId) {
        return repository.findById(paymentId);
    }
}
