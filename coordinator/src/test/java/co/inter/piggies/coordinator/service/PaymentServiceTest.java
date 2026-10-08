package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.domain.PaymentStatus;
import co.inter.piggies.coordinator.service.PaymentService.Registration;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceTest {

    private final InMemoryPaymentRepository repo = new InMemoryPaymentRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
    private final PaymentService service = new PaymentService(repo, clock);

    @Test
    void registersPendingPayment() {
        Registration r = service.register("A", "X", 100, null);

        assertThat(r.created()).isTrue();
        assertThat(r.payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(r.payment().getAmount()).isEqualTo(100);
        assertThat(r.payment().getCreatedAt()).isEqualTo(clock.instant());
        assertThat(repo.store).containsKey(r.payment().getId());
        assertThat(r.payment().toIntent().paymentId()).isEqualTo(r.payment().getId());
    }

    @Test
    void withoutKeyEachCallCreatesNewPayment() {
        UUID a = service.register("A", "X", 1, null).payment().getId();
        UUID b = service.register("A", "X", 1, "  ").payment().getId();

        assertThat(a).isNotEqualTo(b);
        assertThat(repo.store).hasSize(2);
    }

    @Test
    void sameIdempotencyKeyReturnsSamePaymentWithoutCreatingAnother() {
        Registration first = service.register("A", "X", 100, "k1");
        Registration second = service.register("A", "X", 100, "k1");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.payment().getId()).isEqualTo(first.payment().getId());
        assertThat(repo.store).hasSize(1);
    }

    @Test
    void differentKeysCreateDifferentPayments() {
        service.register("A", "X", 100, "k1");
        service.register("A", "X", 100, "k2");

        assertThat(repo.store).hasSize(2);
    }

    @Test
    void raceOnUniqueConstraintFallsBackToWinner() {
        Registration winner = service.register("A", "X", 100, "k1");
        repo.hideNextLookup = true; // a verificação prévia "não vê" o vencedor; o INSERT estoura a UNIQUE

        Registration loser = service.register("A", "X", 100, "k1");

        assertThat(loser.created()).isFalse();
        assertThat(loser.payment().getId()).isEqualTo(winner.payment().getId());
        assertThat(repo.store).hasSize(1);
    }

    @Test
    void findReturnsEmptyForUnknownId() {
        assertThat(service.find(UUID.randomUUID())).isEmpty();
    }
}
