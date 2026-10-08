package co.inter.piggies.merchant.messaging;

import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Property;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Cliente Kafka (bloqueante: retorna o {@link RecordMetadata} após o ack do broker, ou lança exceção).
 * Chave e valor são {@code String} (StringSerializer); o valor é o JSON do {@link PagamentoConfirmadoEvent}.
 */
@KafkaClient(
        id = "pagamento-confirmado-producer",
        acks = KafkaClient.Acknowledge.ALL,
        properties = {
                @Property(name = "enable.idempotence", value = "true"),
                @Property(name = "max.block.ms", value = "5000"),
                @Property(name = "request.timeout.ms", value = "5000"),
                @Property(name = "delivery.timeout.ms", value = "10000")
        })
public interface PagamentoConfirmadoProducer {

    @Topic("${spp.topics.pagamento-confirmado}")
    RecordMetadata send(@KafkaKey String paymentId, String json);
}
