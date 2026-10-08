package co.inter.piggies.coordinator;

import co.inter.piggies.coordinator.client.HttpMerchantGateway;
import co.inter.piggies.coordinator.client.HttpReserveGateway;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.gateway.MerchantGateway.MerchantResult;
import co.inter.piggies.coordinator.gateway.ReserveGateway.ReserveResult;
import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa os clients HTTP contra um servidor falso (JDK {@link HttpServer}) que imita os contratos
 * de Reserve e Merchant. Não precisa de Docker nem de banco.
 */
class HttpGatewaysTest {

    private static HttpServer server;
    private static ApplicationContext context;
    private static HttpReserveGateway reserveGateway;
    private static HttpMerchantGateway merchantGateway;

    private static final List<String> requests = new CopyOnWriteArrayList<>();
    private static final AtomicInteger reserveStatus = new AtomicInteger(201);
    private static final AtomicInteger confirmStatus = new AtomicInteger(200);
    private static final AtomicInteger merchantStatus = new AtomicInteger(200);
    private static volatile String merchantBody = "{\"merchantId\":\"X\",\"active\":true}";
    private static final AtomicInteger receivableStatus = new AtomicInteger(201);

    private static final PaymentIntent INTENT = new PaymentIntent(UUID.randomUUID(), "A", "X", 100);

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);
            String path = exchange.getRequestURI().getPath();
            int status;
            String response = "{}";
            if (path.equals("/reserves")) {
                status = reserveStatus.get();
            } else if (path.endsWith("/confirm")) {
                status = confirmStatus.get();
            } else if (path.endsWith("/release")) {
                status = 200;
            } else if (path.startsWith("/merchants/") && path.endsWith("/status")) {
                status = merchantStatus.get();
                response = merchantBody;
            } else if (path.equals("/receivables")) {
                status = receivableStatus.get();
            } else {
                status = 404;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        context = ApplicationContext.run(noDatabase(url, url));
        reserveGateway = context.getBean(HttpReserveGateway.class);
        merchantGateway = context.getBean(HttpMerchantGateway.class);
    }

    /**
     * Propriedades que deixam o contexto subir sem banco: o pool Hikari não falha no start e o
     * Hibernate não consulta metadados JDBC nem altera o schema. Só os clients HTTP são exercitados.
     */
    private static Map<String, Object> noDatabase(String reserveUrl, String merchantUrl) {
        return Map.of(
                "micronaut.http.services.reserve.url", reserveUrl,
                "micronaut.http.services.merchant.url", merchantUrl,
                "micronaut.server.port", "-1",
                "datasources.default.initialization-fail-timeout", "-1",
                "jpa.default.properties.hibernate.hbm2ddl.auto", "none",
                "jpa.default.properties.hibernate.boot.allow_jdbc_metadata_access", "false",
                "jpa.default.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
    }

    @AfterAll
    static void stop() {
        context.close();
        server.stop(0);
    }

    @BeforeEach
    void reset() {
        requests.clear();
        reserveStatus.set(201);
        confirmStatus.set(200);
        merchantStatus.set(200);
        merchantBody = "{\"merchantId\":\"X\",\"active\":true}";
        receivableStatus.set(201);
    }

    @Test
    void reserveSendsContractBodyAndMapsCreated() {
        assertThat(reserveGateway.reserve(INTENT)).isEqualTo(ReserveResult.RESERVED);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0))
                .startsWith("POST /reserves ")
                .contains("\"paymentId\":\"" + INTENT.paymentId() + "\"")
                .contains("\"clientId\":\"A\"")
                .contains("\"amount\":100");
    }

    @Test
    void reserveAlreadyExistingIsStillReserved() {
        reserveStatus.set(200);
        assertThat(reserveGateway.reserve(INTENT)).isEqualTo(ReserveResult.RESERVED);
    }

    @Test
    void reserveConflictMeansInsufficientFunds() {
        reserveStatus.set(409);
        assertThat(reserveGateway.reserve(INTENT)).isEqualTo(ReserveResult.INSUFFICIENT_FUNDS);
    }

    @Test
    void reserveServerErrorIsUnavailable() {
        reserveStatus.set(500);
        assertThat(reserveGateway.reserve(INTENT)).isEqualTo(ReserveResult.UNAVAILABLE);
    }

    @Test
    void confirmCallsConfirmEndpoint() {
        assertThat(reserveGateway.confirm(INTENT.paymentId())).isTrue();
        assertThat(requests.get(0)).startsWith("POST /reserves/" + INTENT.paymentId() + "/confirm");

        confirmStatus.set(409);
        assertThat(reserveGateway.confirm(INTENT.paymentId())).isFalse();
    }

    @Test
    void releaseCallsReleaseEndpointAndNeverThrows() {
        reserveGateway.release(INTENT.paymentId());
        assertThat(requests.get(0)).startsWith("POST /reserves/" + INTENT.paymentId() + "/release");
    }

    @Test
    void merchantActiveInactiveNotFoundAndUnavailable() {
        assertThat(merchantGateway.validate("X")).isEqualTo(MerchantResult.ACTIVE);
        assertThat(requests.get(0)).startsWith("GET /merchants/X/status");

        merchantBody = "{\"merchantId\":\"Y\",\"active\":false}";
        assertThat(merchantGateway.validate("Y")).isEqualTo(MerchantResult.INACTIVE);

        merchantStatus.set(404);
        assertThat(merchantGateway.validate("Z")).isEqualTo(MerchantResult.NOT_FOUND);

        merchantStatus.set(503);
        assertThat(merchantGateway.validate("X")).isEqualTo(MerchantResult.UNAVAILABLE);
    }

    @Test
    void creditSendsContractBodyAndTreatsCreatedAndOkAsSuccess() {
        assertThat(merchantGateway.credit(INTENT)).isTrue();
        assertThat(requests.get(0))
                .startsWith("POST /receivables ")
                .contains("\"paymentId\":\"" + INTENT.paymentId() + "\"")
                .contains("\"merchantId\":\"X\"")
                .contains("\"amount\":100");

        receivableStatus.set(200);
        assertThat(merchantGateway.credit(INTENT)).isTrue();

        receivableStatus.set(409);
        assertThat(merchantGateway.credit(INTENT)).isFalse();
    }

    @Test
    void connectionRefusedIsUnavailable() {
        try (ApplicationContext other = ApplicationContext.run(noDatabase("http://127.0.0.1:1", "http://127.0.0.1:1"))) {
            assertThat(other.getBean(HttpReserveGateway.class).reserve(INTENT)).isEqualTo(ReserveResult.UNAVAILABLE);
            assertThat(other.getBean(HttpMerchantGateway.class).validate("X")).isEqualTo(MerchantResult.UNAVAILABLE);
        }
    }
}
