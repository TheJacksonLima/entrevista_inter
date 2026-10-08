package co.inter.piggies.reserve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes unitários da lógica de decisão do serviço, sem banco (store em memória). */
class ReserveServiceTest {

    InMemoryReserveStore store;
    ReserveService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryReserveStore();
        store.createAccountIfAbsent("A", 1000);
        store.createAccountIfAbsent("POOR", 0);
        service = new ReserveService(store);
    }

    @Test
    void reserveMovesAvailableToReserved() {
        UUID id = UUID.randomUUID();
        ReserveService.ReserveResult r = service.reserve(id, "A", 100);

        assertThat(r.created()).isTrue();
        assertThat(r.reservation().status()).isEqualTo(ReserveStatus.PENDING);
        assertThat(store.available("A")).isEqualTo(900);
        assertThat(store.reserved("A")).isEqualTo(100);
    }

    @Test
    void reserveIsIdempotentPerPaymentId() {
        UUID id = UUID.randomUUID();
        service.reserve(id, "A", 100);
        ReserveService.ReserveResult again = service.reserve(id, "A", 100);

        assertThat(again.created()).isFalse();
        assertThat(again.reservation().paymentId()).isEqualTo(id);
        assertThat(store.available("A")).isEqualTo(900);
        assertThat(store.reserved("A")).isEqualTo(100);
    }

    @Test
    void reserveWithInsufficientBalanceThrows() {
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), "POOR", 1))
                .isInstanceOf(ReserveException.InsufficientBalance.class);
        assertThat(store.available("POOR")).isZero();
    }

    @Test
    void reserveForUnknownAccountIsInsufficientBalance() {
        assertThatThrownBy(() -> service.reserve(UUID.randomUUID(), "NOPE", 1))
                .isInstanceOf(ReserveException.InsufficientBalance.class);
    }

    @Test
    void confirmDebitsReservedAndIsIdempotent() {
        UUID id = UUID.randomUUID();
        service.reserve(id, "A", 100);

        assertThat(service.confirm(id).status()).isEqualTo(ReserveStatus.CONFIRMED);
        assertThat(service.confirm(id).status()).isEqualTo(ReserveStatus.CONFIRMED);
        assertThat(store.available("A")).isEqualTo(900);
        assertThat(store.reserved("A")).isZero();
    }

    @Test
    void releaseReturnsToAvailableAndIsIdempotent() {
        UUID id = UUID.randomUUID();
        service.reserve(id, "A", 100);

        assertThat(service.release(id).status()).isEqualTo(ReserveStatus.RELEASED);
        assertThat(service.release(id).status()).isEqualTo(ReserveStatus.RELEASED);
        assertThat(store.available("A")).isEqualTo(1000);
        assertThat(store.reserved("A")).isZero();
    }

    @Test
    void confirmAfterReleaseConflicts() {
        UUID id = UUID.randomUUID();
        service.reserve(id, "A", 100);
        service.release(id);

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(ReserveException.InvalidState.class);
        assertThat(store.available("A")).isEqualTo(1000);
    }

    @Test
    void releaseAfterConfirmConflicts() {
        UUID id = UUID.randomUUID();
        service.reserve(id, "A", 100);
        service.confirm(id);

        assertThatThrownBy(() -> service.release(id)).isInstanceOf(ReserveException.InvalidState.class);
        assertThat(store.available("A")).isEqualTo(900);
        assertThat(store.reserved("A")).isZero();
    }

    @Test
    void unknownReservationIsNotFound() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(ReserveException.NotFound.class);
        assertThatThrownBy(() -> service.release(id)).isInstanceOf(ReserveException.NotFound.class);
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ReserveException.NotFound.class);
    }

    @Test
    void getReturnsReservation() {
        UUID id = UUID.randomUUID();
        service.reserve(id, "A", 7);
        assertThat(service.get(id)).isEqualTo(new Reservation(id, "A", 7, ReserveStatus.PENDING));
    }
}
