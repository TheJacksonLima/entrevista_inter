package co.inter.piggies.merchant.messaging;

import co.inter.piggies.merchant.domain.Receivable;
import co.inter.piggies.testing.ContractSchemas;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** O JSON que vai para o Kafka tem de validar contra o contrato (sem Docker). */
class PagamentoConfirmadoJsonTest {

    private Receivable receivable(Instant createdAt, String merchantId) {
        return Receivable.builder()
                .id(UUID.randomUUID()).paymentId(UUID.randomUUID()).merchantId(merchantId)
                .amount(250L).createdAt(createdAt).eventPublished(false).eventId(UUID.randomUUID()).build();
    }

    @Test
    void serializedEventValidatesAgainstSchema() throws Exception {
        try (ObjectMapper.CloseableObjectMapper mapper = ObjectMapper.create(Map.of())) {
            for (Instant when : new Instant[]{
                    Instant.parse("2026-10-08T12:00:00Z"),
                    Instant.parse("2026-10-08T12:00:00.123456Z"),
                    Instant.now()}) {
                Receivable r = receivable(when, "X");
                String json = KafkaPagamentoConfirmadoPublisher.toJson(mapper, r);

                ContractSchemas.assertValid("pagamento-confirmado.v1.schema.json", json);
                assertThat(json).contains("\"paymentId\":\"" + r.getPaymentId() + "\"")
                        .contains("\"eventId\":\"" + r.getEventId() + "\"")
                        .contains("\"amount\":250");
            }
        }
    }

    @Test
    void schemaRejectsExtraFieldsSoTheCheckIsMeaningful() {
        Set<?> errors = ContractSchemas.validate("pagamento-confirmado.v1.schema.json",
                "{\"eventId\":\"" + UUID.randomUUID() + "\",\"paymentId\":\"" + UUID.randomUUID()
                        + "\",\"merchantId\":\"X\",\"amount\":1,\"occurredAt\":\"2026-10-08T12:00:00Z\",\"extra\":1}");
        assertThat(errors).isNotEmpty();
    }
}
