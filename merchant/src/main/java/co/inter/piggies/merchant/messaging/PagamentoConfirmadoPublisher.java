package co.inter.piggies.merchant.messaging;

import co.inter.piggies.merchant.domain.Receivable;

/** Porta de publicação do evento (a implementação real usa Kafka). */
public interface PagamentoConfirmadoPublisher {

    /**
     * Publica e só retorna quando o broker confirmou (acks=all).
     *
     * @throws EventPublicationException se não foi possível confirmar a publicação
     */
    void publish(Receivable receivable);
}
