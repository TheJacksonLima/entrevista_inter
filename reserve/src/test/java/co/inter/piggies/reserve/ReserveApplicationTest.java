package co.inter.piggies.reserve;

import co.inter.piggies.testing.AbstractPostgresIntegrationTest;
import io.micronaut.runtime.EmbeddedApplication;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Smoke test: a aplicação sobe com Postgres real (requer Docker). */
class ReserveApplicationTest extends AbstractPostgresIntegrationTest {

    @Inject
    EmbeddedApplication<?> application;

    @Test
    void applicationStarts() {
        assertThat(application.isRunning()).isTrue();
    }
}
