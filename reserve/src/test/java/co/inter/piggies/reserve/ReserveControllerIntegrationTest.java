package co.inter.piggies.reserve;

import co.inter.piggies.testing.AbstractPostgresIntegrationTest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Integração com Postgres real (Testcontainers; requer Docker). */
class ReserveControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    DataSource dataSource;

    @Inject
    AccountSeeder seeder;

    // ---------- helpers ----------

    private String newAccount(long available) {
        String id = "T-" + UUID.randomUUID();
        seeder.ensureAccount(id, available);
        return id;
    }

    private long[] balance(String clientId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT available, reserved FROM account WHERE client_id = ?")) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return new long[]{rs.getLong(1), rs.getLong(2)};
            }
        }
    }

    private HttpResponse<ReserveResponse> reserve(UUID paymentId, String clientId, long amount) {
        return client.toBlocking().exchange(
                HttpRequest.POST("/reserves", Map.of("paymentId", paymentId.toString(), "clientId", clientId,
                        "amount", amount)),
                ReserveResponse.class);
    }

    private HttpResponse<ReserveResponse> action(UUID paymentId, String action) {
        return client.toBlocking().exchange(
                HttpRequest.POST("/reserves/" + paymentId + "/" + action, ""), ReserveResponse.class);
    }

    private HttpClientResponseException failure(Callable<?> call) {
        try {
            call.call();
        } catch (HttpClientResponseException e) {
            return e;
        } catch (Exception e) {
            throw new AssertionError("esperava HttpClientResponseException", e);
        }
        throw new AssertionError("esperava uma resposta de erro HTTP");
    }

    private void assertProblem(HttpClientResponseException e, HttpStatus status) {
        assertThat(e.getStatus().getCode()).isEqualTo(status.getCode());
        assertThat(e.getResponse().getContentType()).isPresent();
        assertThat(e.getResponse().getContentType().get().toString()).contains("problem+json");
    }

    // ---------- testes ----------

    @Test
    void seedAccountsExist() throws Exception {
        assertThat(balance("A")).containsExactly(1000, 0);
        assertThat(balance("B")).containsExactly(50, 0);
        assertThat(balance("POOR")).containsExactly(0, 0);
    }

    @Test
    void happyPathReserveThenConfirm() throws Exception {
        String client1 = newAccount(1000);
        UUID id = UUID.randomUUID();

        HttpResponse<ReserveResponse> created = reserve(id, client1, 300);
        assertThat(created.getStatus().getCode()).isEqualTo(HttpStatus.CREATED.getCode());
        assertThat(created.body().status()).isEqualTo(ReserveStatus.PENDING);
        assertThat(balance(client1)).containsExactly(700, 300);

        HttpResponse<ReserveResponse> confirmed = action(id, "confirm");
        assertThat(confirmed.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(confirmed.body().status()).isEqualTo(ReserveStatus.CONFIRMED);
        assertThat(balance(client1)).containsExactly(700, 0);

        ReserveResponse got = client.toBlocking().retrieve("/reserves/" + id, ReserveResponse.class);
        assertThat(got).isEqualTo(new ReserveResponse(id, client1, 300, ReserveStatus.CONFIRMED));
    }

    @Test
    void releaseReturnsBalance() throws Exception {
        String client1 = newAccount(500);
        UUID id = UUID.randomUUID();
        reserve(id, client1, 200);

        HttpResponse<ReserveResponse> released = action(id, "release");
        assertThat(released.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(released.body().status()).isEqualTo(ReserveStatus.RELEASED);
        assertThat(balance(client1)).containsExactly(500, 0);
    }

    @Test
    void reserveIsIdempotent() throws Exception {
        String client1 = newAccount(1000);
        UUID id = UUID.randomUUID();

        assertThat(reserve(id, client1, 100).getStatus().getCode()).isEqualTo(HttpStatus.CREATED.getCode());
        HttpResponse<ReserveResponse> again = reserve(id, client1, 100);
        assertThat(again.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(again.body().paymentId()).isEqualTo(id);
        assertThat(balance(client1)).containsExactly(900, 100);
    }

    @Test
    void confirmAndReleaseAreIdempotent() throws Exception {
        String client1 = newAccount(1000);
        UUID confirmId = UUID.randomUUID();
        UUID releaseId = UUID.randomUUID();
        reserve(confirmId, client1, 100);
        reserve(releaseId, client1, 50);

        action(confirmId, "confirm");
        assertThat(action(confirmId, "confirm").body().status()).isEqualTo(ReserveStatus.CONFIRMED);
        action(releaseId, "release");
        assertThat(action(releaseId, "release").body().status()).isEqualTo(ReserveStatus.RELEASED);

        assertThat(balance(client1)).containsExactly(900, 0);
    }

    @Test
    void insufficientBalanceReturns409Problem() throws Exception {
        String client1 = newAccount(50);
        UUID id = UUID.randomUUID();

        HttpClientResponseException e = failure(() -> reserve(id, client1, 51));
        assertProblem(e, HttpStatus.CONFLICT);
        assertThat(balance(client1)).containsExactly(50, 0);
        // a reserva não pode ter sido persistida (rollback)
        assertProblem(failure(() -> client.toBlocking().retrieve("/reserves/" + id, ReserveResponse.class)),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownAccountReturns409() {
        HttpClientResponseException e = failure(() -> reserve(UUID.randomUUID(), "NO-SUCH-" + UUID.randomUUID(), 1));
        assertProblem(e, HttpStatus.CONFLICT);
    }

    @Test
    void invalidBodyReturns400() {
        HttpClientResponseException zero = failure(() -> client.toBlocking().exchange(
                HttpRequest.POST("/reserves", Map.of("paymentId", UUID.randomUUID().toString(), "clientId", "A",
                        "amount", 0))));
        assertThat(zero.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());

        HttpClientResponseException blank = failure(() -> client.toBlocking().exchange(
                HttpRequest.POST("/reserves", Map.of("paymentId", UUID.randomUUID().toString(), "clientId", "",
                        "amount", 10))));
        assertThat(blank.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());

        HttpClientResponseException noPaymentId = failure(() -> client.toBlocking().exchange(
                HttpRequest.POST("/reserves", Map.of("clientId", "A", "amount", 10))));
        assertThat(noPaymentId.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void conflictsBetweenConfirmAndRelease() throws Exception {
        String client1 = newAccount(1000);
        UUID confirmed = UUID.randomUUID();
        UUID released = UUID.randomUUID();
        reserve(confirmed, client1, 100);
        reserve(released, client1, 100);
        action(confirmed, "confirm");
        action(released, "release");

        assertProblem(failure(() -> action(confirmed, "release")), HttpStatus.CONFLICT);
        assertProblem(failure(() -> action(released, "confirm")), HttpStatus.CONFLICT);
        assertThat(balance(client1)).containsExactly(900, 0);
    }

    @Test
    void unknownReservationReturns404() {
        UUID id = UUID.randomUUID();
        assertProblem(failure(() -> action(id, "confirm")), HttpStatus.NOT_FOUND);
        assertProblem(failure(() -> action(id, "release")), HttpStatus.NOT_FOUND);
        assertProblem(failure(() -> client.toBlocking().retrieve("/reserves/" + id, ReserveResponse.class)),
                HttpStatus.NOT_FOUND);
    }

    /** 20 threads reservando 10 cada em conta de 100 => exatamente 10 sucessos; available=0, reserved=100. */
    @Test
    void concurrentReservesNeverOverdraw() throws Exception {
        String client1 = newAccount(100);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID id = UUID.randomUUID();
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return reserve(id, client1, 10).getStatus().getCode();
                } catch (HttpClientResponseException e) {
                    return e.getStatus().getCode();
                }
            }));
        }
        start.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 201) {
                created++;
            } else if (code == 409) {
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(10);
        assertThat(conflicts).isEqualTo(10);
        assertThat(balance(client1)).containsExactly(0, 100);
    }

    /** Mesmo paymentId em paralelo: debita uma única vez (1 x 201, demais 200). */
    @Test
    void concurrentSamePaymentIdDebitsOnce() throws Exception {
        String client1 = newAccount(100);
        UUID id = UUID.randomUUID();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return reserve(id, client1, 10).getStatus().getCode();
            }));
        }
        start.countDown();
        int created = 0;
        int ok = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 201) {
                created++;
            } else if (code == 200) {
                ok++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(ok).isEqualTo(threads - 1);
        assertThat(balance(client1)).containsExactly(90, 10);
    }
}
