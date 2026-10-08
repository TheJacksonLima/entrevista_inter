package co.inter.piggies.reserve;

import java.util.UUID;

/** Visão de domínio (imutável) de uma reserva. */
public record Reservation(UUID paymentId, String clientId, long amount, ReserveStatus status) {

    public Reservation withStatus(ReserveStatus newStatus) {
        return new Reservation(paymentId, clientId, amount, newStatus);
    }
}
