package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.Payment;
import co.inter.piggies.coordinator.domain.PaymentStatus;
import co.inter.piggies.coordinator.domain.RejectionReason;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** reason é nulo (e omitido do JSON) a menos que o status seja REJECTED. */
@Serdeable
public record PaymentStatusResponse(UUID paymentId, String clientId, String merchantId, long amount,
                                    PaymentStatus status, RejectionReason reason) {

    public static PaymentStatusResponse from(Payment p) {
        RejectionReason reason = p.getStatus() == PaymentStatus.REJECTED ? p.getFailureReason() : null;
        return new PaymentStatusResponse(p.getId(), p.getClientId(), p.getMerchantId(), p.getAmount(),
                p.getStatus(), reason);
    }
}
