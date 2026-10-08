package co.inter.piggies.coordinator.domain;

import java.util.UUID;

/**
 * Intenção de pagamento já registrada (status PENDING), entregue ao orquestrador.
 * É o único dado de que o {@code PaymentOrchestrator} precisa — não depende da entidade JPA.
 *
 * @param amount quantidade inteira de Piggies
 */
public record PaymentIntent(UUID paymentId, String clientId, String merchantId, long amount) {
}
