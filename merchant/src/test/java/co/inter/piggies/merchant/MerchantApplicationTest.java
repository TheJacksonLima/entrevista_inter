package co.inter.piggies.merchant;

import co.inter.piggies.testing.AbstractPostgresKafkaIntegrationTest;
import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: a aplicação sobe com Postgres e Kafka reais (requer Docker). */
class MerchantApplicationTest extends AbstractPostgresKafkaIntegrationTest {

    @Inject
    EmbeddedApplication<?> application;

    @Test
    void applicationStarts() {
        assertThat(application.isRunning()).isTrue();
    }
}
