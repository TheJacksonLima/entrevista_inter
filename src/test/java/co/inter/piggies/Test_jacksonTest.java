package co.inter.piggies;

import io.micronaut.runtime.EmbeddedApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import jakarta.inject.Inject;

class Test_jacksonTest extends AbstractIntegrationTest {

    @Inject
    EmbeddedApplication<?> application;

    @Test
    void testItWorks() {
        Assertions.assertTrue(application.isRunning());
    }

}
