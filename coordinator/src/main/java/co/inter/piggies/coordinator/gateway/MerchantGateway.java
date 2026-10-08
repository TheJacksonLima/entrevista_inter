package co.inter.piggies.coordinator.gateway;

import co.inter.piggies.coordinator.domain.PaymentIntent;

/** Visão do Coordinator sobre o PiggiesMerchantService (contrato: contracts/merchant.openapi.yaml). */
public interface MerchantGateway {

    enum MerchantResult {
        /** 200 com {@code active=true}. */
        ACTIVE,
        /** 200 com {@code active=false}. */
        INACTIVE,
        /** 404. */
        NOT_FOUND,
        /** Erro, timeout ou serviço fora do ar. */
        UNAVAILABLE
    }

    /** {@code GET /merchants/{merchantId}/status}. */
    MerchantResult validate(String merchantId);

    /** {@code POST /receivables} (idempotente); {@code true} se o crédito está registrado (200/201). */
    boolean credit(PaymentIntent intent);
}
