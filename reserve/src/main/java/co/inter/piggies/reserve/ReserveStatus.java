package co.inter.piggies.reserve;

/** Estado de uma reserva de saldo. CONFIRMED e RELEASED são terminais. */
public enum ReserveStatus {
    PENDING,
    CONFIRMED,
    RELEASED
}
