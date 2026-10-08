package co.inter.piggies.coordinator.domain;

/** Motivo de um pagamento {@code REJECTED}; coincide com o enum {@code reason} do contrato do Coordinator. */
public enum RejectionReason {
    INSUFFICIENT_FUNDS,
    MERCHANT_INVALID,
    MERCHANT_UNAVAILABLE,
    RESERVE_UNAVAILABLE
}
