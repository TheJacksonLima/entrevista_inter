package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.config.OrchestratorProperties;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.RejectionReason;
import co.inter.piggies.coordinator.gateway.MerchantGateway;
import co.inter.piggies.coordinator.gateway.MerchantGateway.MerchantResult;
import co.inter.piggies.coordinator.gateway.ReserveGateway;
import co.inter.piggies.coordinator.gateway.ReserveGateway.ReserveResult;
import co.inter.piggies.coordinator.port.PaymentStatusUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPaymentOrchestratorTest {

    private static final PaymentIntent INTENT = new PaymentIntent(UUID.randomUUID(), "A", "X", 100);

    private final FakeReserve reserve = new FakeReserve();
    private final FakeMerchant merchant = new FakeMerchant();
    private final RecordingStatus status = new RecordingStatus();
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private DefaultPaymentOrchestrator orchestrator(Duration timeout, int attempts) {
        return new DefaultPaymentOrchestrator(reserve, merchant, status, executor,
                new OrchestratorProperties(timeout, attempts));
    }

    private void run(DefaultPaymentOrchestrator orchestrator) throws Exception {
        orchestrator.submit(INTENT).get(10, TimeUnit.SECONDS);
    }

    @Test
    void happyPathDebitsCreditsAndConfirms() throws Exception {
        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(reserve.calls).containsExactly("reserve", "confirm");
        assertThat(merchant.calls).containsExactly("validate", "credit");
        assertThat(status.confirmed).containsExactly(INTENT.paymentId());
        assertThat(status.rejected).isEmpty();
    }

    @Test
    void reserveAndMerchantValidationRunInParallel() throws Exception {
        // Cada chamada só termina depois que a outra começou: se fossem sequenciais, travaria.
        CountDownLatch bothStarted = new CountDownLatch(2);
        reserve.onReserve = i -> awaitOther(bothStarted, ReserveResult.RESERVED);
        merchant.onValidate = id -> awaitOther(bothStarted, MerchantResult.ACTIVE);

        run(orchestrator(Duration.ofSeconds(5), 1));

        assertThat(status.confirmed).containsExactly(INTENT.paymentId());
    }

    private static <T> T awaitOther(CountDownLatch latch, T result) {
        latch.countDown();
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a outra etapa não rodou em paralelo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    @Test
    void insufficientFundsRejectsWithoutReleaseOrDebit() throws Exception {
        reserve.onReserve = i -> ReserveResult.INSUFFICIENT_FUNDS;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(status.rejected).containsExactly(RejectionReason.INSUFFICIENT_FUNDS);
        assertThat(status.confirmed).isEmpty();
        assertThat(reserve.calls).doesNotContain("release", "confirm");
        assertThat(merchant.calls).doesNotContain("credit");
    }

    @Test
    void inactiveMerchantReleasesReservationAndRejects() throws Exception {
        merchant.onValidate = id -> MerchantResult.INACTIVE;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(status.rejected).containsExactly(RejectionReason.MERCHANT_INVALID);
        assertThat(reserve.calls).contains("release").doesNotContain("confirm");
        assertThat(merchant.calls).doesNotContain("credit");
    }

    @Test
    void unknownMerchantIsInvalid() throws Exception {
        merchant.onValidate = id -> MerchantResult.NOT_FOUND;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(status.rejected).containsExactly(RejectionReason.MERCHANT_INVALID);
        assertThat(reserve.calls).contains("release");
    }

    @Test
    void merchantUnavailableReleasesReservationAndRejects() throws Exception {
        merchant.onValidate = id -> MerchantResult.UNAVAILABLE;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(status.rejected).containsExactly(RejectionReason.MERCHANT_UNAVAILABLE);
        assertThat(reserve.calls).contains("release");
    }

    @Test
    void reserveUnavailableRejectsAndReleasesDefensively() throws Exception {
        reserve.onReserve = i -> ReserveResult.UNAVAILABLE;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(status.rejected).containsExactly(RejectionReason.RESERVE_UNAVAILABLE);
        assertThat(reserve.calls).contains("release");
    }

    @Test
    void slowReserveTimesOutAndIsRejected() throws Exception {
        CountDownLatch unblock = new CountDownLatch(1);
        reserve.onReserve = i -> {
            try {
                unblock.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ReserveResult.RESERVED;
        };

        try {
            run(orchestrator(Duration.ofMillis(200), 3));
        } finally {
            unblock.countDown();
        }

        assertThat(status.rejected).containsExactly(RejectionReason.RESERVE_UNAVAILABLE);
        assertThat(reserve.calls).contains("release");
    }

    @Test
    void gatewayExceptionIsTreatedAsUnavailable() throws Exception {
        merchant.onValidate = id -> {
            throw new IllegalStateException("boom");
        };

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(status.rejected).containsExactly(RejectionReason.MERCHANT_UNAVAILABLE);
    }

    @Test
    void creditIsRetriedUntilItSucceeds() throws Exception {
        AtomicInteger credits = new AtomicInteger();
        merchant.onCredit = i -> credits.incrementAndGet() >= 3;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(credits).hasValue(3);
        assertThat(status.confirmed).containsExactly(INTENT.paymentId());
    }

    @Test
    void debitedButNotCreditedStaysPendingAndIsNeverRejected() throws Exception {
        merchant.onCredit = i -> false;

        run(orchestrator(Duration.ofSeconds(2), 3));

        assertThat(reserve.calls).contains("confirm");
        assertThat(status.confirmed).isEmpty();
        assertThat(status.rejected).isEmpty();
        assertThat(reserve.calls).doesNotContain("release");
    }

    @Test
    void debitFailureStaysPendingAndNeverCredits() throws Exception {
        reserve.onConfirm = id -> false;

        run(orchestrator(Duration.ofSeconds(2), 2));

        assertThat(reserve.calls.stream().filter("confirm"::equals)).hasSize(2);
        assertThat(merchant.calls).doesNotContain("credit");
        assertThat(status.confirmed).isEmpty();
        assertThat(status.rejected).isEmpty();
    }

    @Test
    void unexpectedErrorWhileUpdatingStatusDoesNotEscape() {
        PaymentStatusUpdater exploding = new PaymentStatusUpdater() {
            @Override
            public void markConfirmed(UUID paymentId) {
                throw new IllegalStateException("db down");
            }

            @Override
            public void markRejected(UUID paymentId, RejectionReason reason) {
                throw new IllegalStateException("db down");
            }
        };
        var orchestrator = new DefaultPaymentOrchestrator(reserve, merchant, exploding, executor,
                new OrchestratorProperties(Duration.ofSeconds(2), 3));

        assertThat(orchestrator.submit(INTENT)).succeedsWithin(Duration.ofSeconds(5));
    }

    // ---- fakes ---------------------------------------------------------------------------------

    static class FakeReserve implements ReserveGateway {
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile Function<PaymentIntent, ReserveResult> onReserve = i -> ReserveResult.RESERVED;
        volatile Predicate<UUID> onConfirm = id -> true;

        @Override
        public ReserveResult reserve(PaymentIntent intent) {
            calls.add("reserve");
            return onReserve.apply(intent);
        }

        @Override
        public boolean confirm(UUID paymentId) {
            calls.add("confirm");
            return onConfirm.test(paymentId);
        }

        @Override
        public void release(UUID paymentId) {
            calls.add("release");
        }
    }

    static class FakeMerchant implements MerchantGateway {
        final List<String> calls = new CopyOnWriteArrayList<>();
        volatile Function<String, MerchantResult> onValidate = id -> MerchantResult.ACTIVE;
        volatile Predicate<PaymentIntent> onCredit = i -> true;

        @Override
        public MerchantResult validate(String merchantId) {
            calls.add("validate");
            return onValidate.apply(merchantId);
        }

        @Override
        public boolean credit(PaymentIntent intent) {
            calls.add("credit");
            return onCredit.test(intent);
        }
    }

    static class RecordingStatus implements PaymentStatusUpdater {
        final List<UUID> confirmed = new CopyOnWriteArrayList<>();
        final List<RejectionReason> rejected = new CopyOnWriteArrayList<>();

        @Override
        public void markConfirmed(UUID paymentId) {
            confirmed.add(paymentId);
        }

        @Override
        public void markRejected(UUID paymentId, RejectionReason reason) {
            rejected.add(reason);
        }
    }
}
