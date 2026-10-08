package co.inter.piggies.merchant.messaging;

import co.inter.piggies.merchant.domain.Receivable;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
public class KafkaPagamentoConfirmadoPublisher implements PagamentoConfirmadoPublisher {

    private final PagamentoConfirmadoProducer producer;
    private final ObjectMapper objectMapper;

    public KafkaPagamentoConfirmadoPublisher(PagamentoConfirmadoProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(Receivable receivable) {
        try {
            String json = toJson(objectMapper, receivable);
            producer.send(receivable.getPaymentId().toString(), json);
        } catch (IOException | RuntimeException e) {
            throw new EventPublicationException(
                    "Falha ao publicar PagamentoConfirmado do pagamento " + receivable.getPaymentId(), e);
        }
    }

    /** Monta o evento a partir do recebível (eventId e occurredAt são os armazenados) e serializa. */
    public static String toJson(ObjectMapper objectMapper, Receivable r) throws IOException {
        PagamentoConfirmadoEvent event = new PagamentoConfirmadoEvent(
                r.getEventId().toString(),
                r.getPaymentId().toString(),
                r.getMerchantId(),
                r.getAmount(),
                r.getCreatedAt().toString());
        return objectMapper.writeValueAsString(event);
    }
}
