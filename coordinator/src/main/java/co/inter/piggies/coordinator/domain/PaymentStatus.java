package co.inter.piggies.coordinator.domain;

/** Estado do pagamento; coincide com o enum PaymentStatus do contrato. */
public enum PaymentStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
