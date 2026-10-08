package co.inter.piggies.reserve;

import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Serdeable
public record ReserveResponse(UUID paymentId, String clientId, long amount, ReserveStatus status) {

    public static ReserveResponse from(Reservation r) {
        return new ReserveResponse(r.paymentId(), r.clientId(), r.amount(), r.status());
    }
}
