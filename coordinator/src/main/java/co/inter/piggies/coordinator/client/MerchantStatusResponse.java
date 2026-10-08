package co.inter.piggies.coordinator.client;

import io.micronaut.serde.annotation.Serdeable;

/** Resposta de {@code GET /merchants/{id}/status} (contracts/merchant.openapi.yaml → MerchantStatus). */
@Serdeable
record MerchantStatusResponse(String merchantId, boolean active) {
}
