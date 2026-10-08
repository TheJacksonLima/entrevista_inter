package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.domain.Payment;
import co.inter.piggies.coordinator.domain.PaymentStatus;
import co.inter.piggies.coordinator.domain.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryPaymentStatusUpdaterTest {

    private final InMemoryPaymentRepository repo = new InMemoryPaymentRepository();
    private final Instant created = Instant.parse("2026-10-08T10:00:00Z");
    private final Instant later = Instant.parse("2026-10-08T10:00:05Z");
    private final RepositoryPaymentStatusUpdater updater =
            new RepositoryPaymentStatusUpdater(repo, Clock.fixed(later, ZoneOffset.UTC));

    private UUID pending() {
        Payment p = Payment.pending(UUID.randomUUID(), "A", "X", 100, null, created);
        repo.save(p);
        return p.getId();
    }

    @Test
    void markConfirmedUpdatesStatusAndTimestamp() {
        UUID id = pending();

        updater.markConfirmed(id);

        Payment p = repo.store.get(id);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(p.getFailureReason()).isNull();
        assertThat(p.getUpdatedAt()).isEqualTo(later);
        assertThat(p.getCreatedAt()).isEqualTo(created);
    }

    @Test
    void markRejectedStoresReason() {
        UUID id = pending();

        updater.markRejected(id, RejectionReason.INSUFFICIENT_FUNDS);

        Payment p = repo.store.get(id);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(p.getFailureReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void confirmedNeverRegresses() {
        UUID id = pending();
        updater.markConfirmed(id);

        updater.markRejected(id, RejectionReason.RESERVE_UNAVAILABLE);
        updater.markConfirmed(id);

        Payment p = repo.store.get(id);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(p.getFailureReason()).isNull();
    }

    @Test
    void rejectedIsNotOverwritten() {
        UUID id = pending();
        updater.markRejected(id, RejectionReason.MERCHANT_INVALID);

        updater.markConfirmed(id);
        updater.markRejected(id, RejectionReason.INSUFFICIENT_FUNDS);

        Payment p = repo.store.get(id);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(p.getFailureReason()).isEqualTo(RejectionReason.MERCHANT_INVALID);
    }

    @Test
    void unknownPaymentIsIgnored() {
        updater.markConfirmed(UUID.randomUUID());
        assertThat(repo.store).isEmpty();
    }
}
