package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.domain.Payment;
import co.inter.piggies.coordinator.domain.PaymentStatus;
import co.inter.piggies.coordinator.domain.RejectionReason;
import co.inter.piggies.coordinator.port.PaymentStatusUpdater;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.UUID;

/** Persiste o resultado da orquestração. Só transiciona a partir de PENDING (idempotente, nunca regride). */
@Singleton
public class RepositoryPaymentStatusUpdater implements PaymentStatusUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryPaymentStatusUpdater.class);

    private final PaymentRepository repository;
    private final Clock clock;

    @Inject
    public RepositoryPaymentStatusUpdater(PaymentRepository repository) {
        this(repository, Clock.systemUTC());
    }

    RepositoryPaymentStatusUpdater(PaymentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void markConfirmed(UUID paymentId) {
        transition(paymentId, PaymentStatus.CONFIRMED, null);
    }

    @Override
    @Transactional
    public void markRejected(UUID paymentId, RejectionReason reason) {
        transition(paymentId, PaymentStatus.REJECTED, reason);
    }

    private void transition(UUID paymentId, PaymentStatus target, RejectionReason reason) {
        Payment payment = repository.findById(paymentId).orElse(null);
        if (payment == null) {
            LOG.warn("Pagamento {} não encontrado ao marcar {}", paymentId, target);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            LOG.info("Pagamento {} já está {}; ignorando transição para {}", paymentId, payment.getStatus(), target);
            return;
        }
        payment.setStatus(target);
        payment.setFailureReason(reason);
        payment.setUpdatedAt(clock.instant());
        repository.update(payment);
    }
}
