package co.inter.piggies.coordinator.web;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Serdeable
public record PaymentRequest(
        @NotBlank String clientId,
        @NotBlank String merchantId,
        @NotNull @Min(1) Long amount) {
}
