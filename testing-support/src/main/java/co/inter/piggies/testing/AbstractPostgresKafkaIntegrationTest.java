package co.inter.piggies.testing;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link AbstractPostgresIntegrationTest} + Kafka. Usada pelo MerchantService, que publica
 * o evento PagamentoConfirmado. Requer Docker.
 */
public abstract class AbstractPostgresKafkaIntegrationTest extends AbstractPostgresIntegrationTest {

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    static {
        KAFKA.start();
    }

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("kafka.bootstrap.servers", KAFKA.getBootstrapServers());
        return props;
    }
}
