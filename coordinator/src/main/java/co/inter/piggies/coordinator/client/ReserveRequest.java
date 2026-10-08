package co.inter.piggies.coordinator.client;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/** Body de {@code POST /reserves} (contracts/reserve.openapi.yaml → ReserveRequest). */
@Serdeable
record ReserveRequest(UUID paymentId, String clientId, long amount) {
}
