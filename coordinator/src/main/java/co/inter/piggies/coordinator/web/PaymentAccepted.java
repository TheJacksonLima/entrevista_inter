package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.PaymentStatus;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record PaymentAccepted(UUID paymentId, PaymentStatus status) {
}
