package co.inter.piggies.merchant.api;

import co.inter.piggies.testing.AbstractPostgresKafkaIntegrationTest;
import co.inter.piggies.testing.ContractSchemas;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Requer Docker (Postgres + Kafka via Testcontainers). */
class MerchantApiIntegrationTest extends AbstractPostgresKafkaIntegrationTest {

    static final String TOPIC = "piggies.pagamento-confirmado.v1";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ObjectMapper objectMapper;

    // ---- helpers -------------------------------------------------------------------------------

    private HttpResponse<String> get(String uri) {
        try {
            return client.toBlocking().exchange(HttpRequest.GET(uri), String.class);
        } catch (HttpClientResponseException e) {
            return fromError(e);
        }
    }

    private HttpResponse<String> post(String json) {
        try {
            return client.toBlocking().exchange(HttpRequest.POST("/receivables", json)
                    .contentType("application/json"), String.class);
        } catch (HttpClientResponseException e) {
            return fromError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> fromError(HttpClientResponseException e) {
        return (HttpResponse<String>) e.getResponse();
    }

    private static String body(UUID paymentId, String merchantId, Object amount) {
        return "{\"paymentId\":\"" + paymentId + "\",\"merchantId\":\"" + merchantId + "\",\"amount\":" + amount + "}";
    }

    private Map<String, Object> json(String s) throws Exception {
        return objectMapper.readValue(s, Argument.mapOf(Argument.STRING, Argument.OBJECT_ARGUMENT));
    }

    private List<ConsumerRecord<String, String>> eventsFor(UUID paymentId, int atLeast, Duration wait) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + wait.toNanos();
            while (System.nanoTime() < deadline && found.size() < atLeast) {
                for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
                    if (paymentId.toString().equals(r.key())) {
                        found.add(r);
                    }
                }
            }
            // janela extra para pegar publicações duplicadas indevidas
            for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofSeconds(2))) {
                if (paymentId.toString().equals(r.key())) {
                    found.add(r);
                }
            }
        }
        return found;
    }

    // ---- GET /merchants/{id}/status ------------------------------------------------------------

    @Test
    void statusOfActiveInactiveAndUnknownMerchant() throws Exception {
        HttpResponse<String> x = get("/merchants/X/status");
        assertThat(x.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(json(x.body())).containsEntry("merchantId", "X").containsEntry("active", true);

        HttpResponse<String> y = get("/merchants/Y/status");
        assertThat(y.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(json(y.body())).containsEntry("merchantId", "Y").containsEntry("active", false);

        HttpResponse<String> z = get("/merchants/Z/status");
        assertThat(z.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(z.getContentType().orElseThrow().toString()).startsWith("application/problem+json");
    }

    // ---- POST /receivables ---------------------------------------------------------------------

    @Test
    void createsReceivablePublishesValidEventAndIsIdempotent() throws Exception {
        UUID paymentId = UUID.randomUUID();

        HttpResponse<String> first = post(body(paymentId, "X", 100));
        assertThat(first.getStatus().getCode()).isEqualTo(HttpStatus.CREATED.getCode());
        Map<String, Object> created = json(first.body());
        assertThat(created).containsEntry("paymentId", paymentId.toString())
                .containsEntry("merchantId", "X");
        assertThat(((Number) created.get("amount")).longValue()).isEqualTo(100L);

        HttpResponse<String> second = post(body(paymentId, "X", 100));
        assertThat(second.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(json(second.body()).get("receivableId")).isEqualTo(created.get("receivableId"));

        // 1 recebível
        HttpResponse<String> list = get("/merchants/X/receivables");
        assertThat(list.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(list.body().split(paymentId.toString(), -1).length - 1).isEqualTo(1);

        // 1 evento, chave = paymentId, payload conforme o contrato
        List<ConsumerRecord<String, String>> events = eventsFor(paymentId, 1, Duration.ofSeconds(20));
        assertThat(events).hasSize(1);
        ConsumerRecord<String, String> record = events.get(0);
        assertThat(record.key()).isEqualTo(paymentId.toString());
        ContractSchemas.assertValid("pagamento-confirmado.v1.schema.json", record.value());
        Map<String, Object> event = json(record.value());
        assertThat(event).containsEntry("paymentId", paymentId.toString())
                .containsEntry("merchantId", "X");
        assertThat(((Number) event.get("amount")).longValue()).isEqualTo(100L);
    }

    @Test
    void unknownMerchantIs404WithoutEvent() throws Exception {
        UUID paymentId = UUID.randomUUID();
        HttpResponse<String> r = post(body(paymentId, "Z", 100));
        assertThat(r.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(r.getContentType().orElseThrow().toString()).startsWith("application/problem+json");
        assertThat(eventsFor(paymentId, 1, Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void inactiveMerchantIs409WithoutEvent() throws Exception {
        UUID paymentId = UUID.randomUUID();
        HttpResponse<String> r = post(body(paymentId, "Y", 100));
        assertThat(r.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
        assertThat(r.getContentType().orElseThrow().toString()).startsWith("application/problem+json");
        assertThat(eventsFor(paymentId, 1, Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void invalidBodyIs400() {
        UUID id = UUID.randomUUID();
        assertThat(post(body(id, "X", 0)).getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(post(body(id, "X", -5)).getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(post(body(id, "", 10)).getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(post("{\"merchantId\":\"X\",\"amount\":10}").getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(post("{\"paymentId\":\"nao-e-uuid\",\"merchantId\":\"X\",\"amount\":10}").getStatus().getCode())
                .isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void listReceivablesOfUnknownMerchantIs404() {
        assertThat(get("/merchants/Z/receivables").getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }
}
