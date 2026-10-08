package co.inter.piggies.merchant.service;

import java.util.UUID;

/**
 * O recebível está gravado, mas o evento não pôde ser publicado: responde 503 para o chamador repetir
 * (o retry com o mesmo paymentId republica, pois {@code event_published = false}).
 */
public class EventNotPublishedException extends DomainException {

    public EventNotPublishedException(UUID paymentId, Throwable cause) {
        super(503, "Event not published",
                "Recebível do pagamento " + paymentId + " gravado, mas o evento não foi publicado; tente novamente");
        initCause(cause);
    }
}
