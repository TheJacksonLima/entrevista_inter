package co.inter.piggies.coordinator.client;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Body de {@code POST /receivables} (contracts/merchant.openapi.yaml → ReceivableRequest). */
@Serdeable
record ReceivableRequest(UUID paymentId, String merchantId, long amount) {
}
