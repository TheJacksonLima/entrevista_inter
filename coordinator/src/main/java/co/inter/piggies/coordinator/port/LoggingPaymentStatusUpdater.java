package co.inter.piggies.coordinator.port;

import co.inter.piggies.coordinator.domain.RejectionReason;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Implementação provisória: só registra em log. Por ser {@link Secondary}, qualquer outro bean
 * {@code PaymentStatusUpdater} (o real, do Dev 1) tem prioridade — não é preciso remover esta classe.
 */
@Singleton
@Secondary
class LoggingPaymentStatusUpdater implements PaymentStatusUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingPaymentStatusUpdater.class);

    @Override
    public void markConfirmed(UUID paymentId) {
        LOG.info("Pagamento {} CONFIRMED (updater provisório: nada persistido)", paymentId);
    }

    @Override
    public void markRejected(UUID paymentId, RejectionReason reason) {
        LOG.info("Pagamento {} REJECTED ({}) (updater provisório: nada persistido)", paymentId, reason);
    }
}
