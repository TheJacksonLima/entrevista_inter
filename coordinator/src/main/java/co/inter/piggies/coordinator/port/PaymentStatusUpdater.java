package co.inter.piggies.coordinator.port;

import co.inter.piggies.coordinator.domain.RejectionReason;

import java.util.UUID;

/**
 * Porta pela qual o orquestrador devolve o resultado à borda. Implementada pelo Dev 1
 * (persistência da tabela {@code payment}); até lá {@code LoggingPaymentStatusUpdater} apenas loga.
 */
public interface PaymentStatusUpdater {

    /** Débito confirmado e crédito registrado → {@code CONFIRMED}. */
    void markConfirmed(UUID paymentId);

    /** Pagamento recusado → {@code REJECTED} com o motivo. */
    void markRejected(UUID paymentId, RejectionReason reason);
}
