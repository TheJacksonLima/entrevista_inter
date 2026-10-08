package co.inter.piggies.coordinator.gateway;

import co.inter.piggies.coordinator.domain.PaymentIntent;

import java.util.UUID;

/** Visão do Coordinator sobre o PiggiesReserveService (contrato: contracts/reserve.openapi.yaml). */
public interface ReserveGateway {

    enum ReserveResult {
        /** 200/201: saldo separado. */
        RESERVED,
        /** 409: saldo insuficiente. */
        INSUFFICIENT_FUNDS,
        /** Erro, timeout ou serviço fora do ar — não se sabe se a reserva foi criada. */
        UNAVAILABLE
    }

    /** {@code POST /reserves}. */
    ReserveResult reserve(PaymentIntent intent);

    /** {@code POST /reserves/{id}/confirm}; {@code true} se o débito ficou CONFIRMED. */
    boolean confirm(UUID paymentId);

    /** {@code POST /reserves/{id}/release}; compensação best effort, nunca lança. */
    void release(UUID paymentId);
}
