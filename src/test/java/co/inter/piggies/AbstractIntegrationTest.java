package co.inter.piggies;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/**
 * Base para testes de integração: sobe um Testcontainer para cada imagem Docker
 * (PostgreSQL e Kafka) uma única vez e aponta a aplicação Micronaut para eles.
 */
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIntegrationTest implements TestPropertyProvider {

    // Testcontainer 1: PostgreSQL
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    // Testcontainer 2: Kafka
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Override
    public Map<String, String> getProperties() {
        return Map.of(
                "datasources.default.url", POSTGRES.getJdbcUrl(),
                "datasources.default.username", POSTGRES.getUsername(),
                "datasources.default.password", POSTGRES.getPassword(),
                "datasources.default.driver-class-name", "org.postgresql.Driver",
                "jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop",
                "kafka.bootstrap.servers", KAFKA.getBootstrapServers()
        );
    }
}
