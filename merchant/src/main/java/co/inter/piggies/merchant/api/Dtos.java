package co.inter.piggies.merchant.api;

import co.inter.piggies.merchant.domain.Receivable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** DTOs HTTP (contracts/merchant.openapi.yaml). */
public final class Dtos {

    private Dtos() {
    }

    @Serdeable
    public record MerchantStatusResponse(String merchantId, boolean active) {
    }

    @Serdeable
    public record ReceivableRequest(
            @NotNull UUID paymentId,
            @NotBlank String merchantId,
            @NotNull @Min(1) Long amount) {
    }

    /** {@code createdAt} como String ISO-8601 UTC (formato date-time do contrato). */
    @Serdeable
    public record ReceivableResponse(
            String receivableId,
            String paymentId,
            String merchantId,
            long amount,
            String createdAt) {

        public static ReceivableResponse from(Receivable r) {
            return new ReceivableResponse(r.getId().toString(), r.getPaymentId().toString(),
                    r.getMerchantId(), r.getAmount(), r.getCreatedAt().toString());
        }
    }

    /** RFC 7807. */
    @Serdeable
    public record Problem(String type, String title, int status, String detail) {
    }
}
