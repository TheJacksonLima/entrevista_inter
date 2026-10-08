package co.inter.piggies.reserve;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Serdeable
public record ReserveRequest(
        @NotNull UUID paymentId,
        @NotBlank String clientId,
        @Min(1) long amount) {
}
