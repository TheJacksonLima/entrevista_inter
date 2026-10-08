package co.inter.piggies.testing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractSchemasTest {

    private static final String SCHEMA = "pagamento-confirmado.v1.schema.json";

    @Test
    void acceptsValidEvent() {
        String json = """
                {"eventId":"6f1c1a52-6a3b-4a53-9f0e-0c4c3f1f7a10",
                 "paymentId":"0b8a6d3e-2a8f-4a43-9c41-5c1d9f6f2b11",
                 "merchantId":"X","amount":100,"occurredAt":"2026-10-08T17:00:00Z"}""";

        assertThat(ContractSchemas.validate(SCHEMA, json)).isEmpty();
    }

    @Test
    void rejectsMissingFieldsAndNonPositiveAmount() {
        String json = """
                {"eventId":"6f1c1a52-6a3b-4a53-9f0e-0c4c3f1f7a10","merchantId":"X","amount":0}""";

        assertThat(ContractSchemas.validate(SCHEMA, json)).isNotEmpty();
        assertThatThrownBy(() -> ContractSchemas.assertValid(SCHEMA, json))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void rejectsUnknownProperties() {
        String json = """
                {"eventId":"6f1c1a52-6a3b-4a53-9f0e-0c4c3f1f7a10",
                 "paymentId":"0b8a6d3e-2a8f-4a43-9c41-5c1d9f6f2b11",
                 "merchantId":"X","amount":100,"occurredAt":"2026-10-08T17:00:00Z","extra":true}""";

        assertThat(ContractSchemas.validate(SCHEMA, json)).isNotEmpty();
    }
}
