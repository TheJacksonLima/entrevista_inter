package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.PaymentStatus;
import co.inter.piggies.coordinator.domain.RejectionReason;
import co.inter.piggies.coordinator.port.PaymentOrchestrator;
import co.inter.piggies.coordinator.service.DefaultPaymentOrchestrator;
import co.inter.piggies.coordinator.port.PaymentStatusUpdater;
import co.inter.piggies.testing.AbstractPostgresIntegrationTest;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Integração HTTP + Postgres real (requer Docker). O orquestrador é substituído por um fake. */
@SuppressWarnings({"unchecked", "rawtypes"})
class PaymentControllerTest extends AbstractPostgresIntegrationTest {

    @Singleton
    @Replaces(DefaultPaymentOrchestrator.class)
    static class RecordingOrchestrator implements PaymentOrchestrator {
        final List<PaymentIntent> submitted = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<Void> submit(PaymentIntent intent) {
            submitted.add(intent);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    RecordingOrchestrator orchestrator;

    @Inject
    PaymentStatusUpdater updater;

    @BeforeEach
    void reset() {
        orchestrator.submitted.clear();
    }

    private HttpResponse<Map> post(Map<String, Object> body, String idempotencyKey) {
        var req = HttpRequest.POST("/payments", body);
        if (idempotencyKey != null) {
            req = req.header("Idempotency-Key", idempotencyKey);
        }
        return client.toBlocking().exchange(req, Map.class);
    }

    private HttpClientResponseException postExpectingError(Map<String, Object> body) {
        try {
            post(body, null);
        } catch (HttpClientResponseException e) {
            return e;
        }
        throw new AssertionError("esperava erro HTTP");
    }

    @Test
    void postReturns202ThenGetShowsPending() {
        HttpResponse<Map> res = post(Map.of("clientId", "A", "merchantId", "X", "amount", 100), null);

        assertThat(res.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        String id = (String) res.body().get("paymentId");
        assertThat(res.body().get("status")).isEqualTo("PENDING");
        assertThat(res.header("Location")).isEqualTo("/payments/" + id);
        assertThat(orchestrator.submitted).hasSize(1);
        assertThat(orchestrator.submitted.get(0).paymentId()).isEqualTo(UUID.fromString(id));

        Map<String, Object> got = (Map<String, Object>) client.toBlocking().retrieve("/payments/" + id, Map.class);
        assertThat(got.get("paymentId")).isEqualTo(id);
        assertThat(got.get("clientId")).isEqualTo("A");
        assertThat(got.get("merchantId")).isEqualTo("X");
        assertThat(((Number) got.get("amount")).longValue()).isEqualTo(100L);
        assertThat(got.get("status")).isEqualTo("PENDING");
        assertThat(got).doesNotContainKey("reason");
    }

    @Test
    void invalidBodyReturns400Problem() {
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("clientId", "A", "merchantId", "X", "amount", 0),
                Map.<String, Object>of("clientId", "", "merchantId", "X", "amount", 10),
                Map.<String, Object>of("clientId", "A", "merchantId", " ", "amount", 10),
                Map.<String, Object>of("clientId", "A", "merchantId", "X"))) {
            HttpClientResponseException e = postExpectingError(body);
            assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
            assertThat(e.getResponse().getContentType().orElseThrow().toString())
                    .contains("application/problem+json");
        }
        assertThat(orchestrator.submitted).isEmpty();
    }

    @Test
    void unknownPaymentReturns404Problem() {
        try {
            client.toBlocking().exchange(HttpRequest.GET("/payments/" + UUID.randomUUID()), Map.class);
            throw new AssertionError("esperava 404");
        } catch (HttpClientResponseException e) {
            assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
            assertThat(e.getResponse().getContentType().orElseThrow().toString())
                    .contains("application/problem+json");
        }
    }

    @Test
    void repeatedIdempotencyKeyReturnsSamePaymentAndOrchestratesOnce() {
        String key = "key-" + UUID.randomUUID();
        Map<String, Object> body = Map.of("clientId", "A", "merchantId", "X", "amount", 50);

        HttpResponse<Map> first = post(body, key);
        HttpResponse<Map> second = post(body, key);

        assertThat(second.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());
        assertThat(second.body().get("paymentId")).isEqualTo(first.body().get("paymentId"));
        assertThat(second.header("Location")).isEqualTo(first.header("Location"));
        assertThat(orchestrator.submitted).hasSize(1);
    }

    @Test
    void updaterConfirmsAndRejectsAndIsVisibleViaGet() {
        String confirmed = (String) post(Map.of("clientId", "A", "merchantId", "X", "amount", 1), null)
                .body().get("paymentId");
        String rejected = (String) post(Map.of("clientId", "A", "merchantId", "X", "amount", 2), null)
                .body().get("paymentId");

        updater.markConfirmed(UUID.fromString(confirmed));
        updater.markRejected(UUID.fromString(rejected), RejectionReason.INSUFFICIENT_FUNDS);
        updater.markRejected(UUID.fromString(confirmed), RejectionReason.RESERVE_UNAVAILABLE); // não regride

        Map<String, Object> c = (Map<String, Object>) client.toBlocking().retrieve("/payments/" + confirmed, Map.class);
        assertThat(c.get("status")).isEqualTo(PaymentStatus.CONFIRMED.name());
        assertThat(c).doesNotContainKey("reason");

        Map<String, Object> r = (Map<String, Object>) client.toBlocking().retrieve("/payments/" + rejected, Map.class);
        assertThat(r.get("status")).isEqualTo("REJECTED");
        assertThat(r.get("reason")).isEqualTo("INSUFFICIENT_FUNDS");
    }
}
