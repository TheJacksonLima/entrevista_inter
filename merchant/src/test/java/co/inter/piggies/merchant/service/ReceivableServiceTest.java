package co.inter.piggies.merchant.service;

import co.inter.piggies.merchant.domain.Merchant;
import co.inter.piggies.merchant.domain.MerchantStatus;
import co.inter.piggies.merchant.domain.Receivable;
import co.inter.piggies.merchant.messaging.EventPublicationException;
import co.inter.piggies.merchant.messaging.PagamentoConfirmadoPublisher;
import co.inter.piggies.merchant.repository.ReceivableStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceivableServiceTest {

    /** Store em memória; {@code raceOnNextInsert} simula outra requisição gravando antes do INSERT. */
    static class FakeStore implements ReceivableStore {
        final Map<String, Merchant> merchants = new LinkedHashMap<>();
        final Map<UUID, Receivable> byPayment = new LinkedHashMap<>();
        Receivable raceWinner;

        @Override
        public Optional<Merchant> findMerchant(String id) {
            return Optional.ofNullable(merchants.get(id));
        }

        @Override
        public Optional<Receivable> findByPaymentId(UUID paymentId) {
            return Optional.ofNullable(byPayment.get(paymentId));
        }

        @Override
        public List<Receivable> findByMerchant(String merchantId) {
            return byPayment.values().stream().filter(r -> r.getMerchantId().equals(merchantId)).toList();
        }

        @Override
        public boolean insertIfAbsent(Receivable r) {
            if (raceWinner != null) {
                byPayment.put(raceWinner.getPaymentId(), raceWinner);
                raceWinner = null;
            }
            if (byPayment.containsKey(r.getPaymentId())) {
                return false;
            }
            byPayment.put(r.getPaymentId(), r);
            return true;
        }

        @Override
        public void markEventPublished(UUID receivableId) {
            byPayment.values().stream().filter(r -> r.getId().equals(receivableId))
                    .forEach(r -> r.setEventPublished(true));
        }

        @Override
        public void seedIfAbsent(List<Merchant> list) {
            list.forEach(m -> merchants.putIfAbsent(m.getId(), m));
        }
    }

    static class FakePublisher implements PagamentoConfirmadoPublisher {
        final List<Receivable> published = new ArrayList<>();
        final List<Boolean> storedAtPublish = new ArrayList<>();
        boolean fail;
        FakeStore store;

        @Override
        public void publish(Receivable r) {
            if (fail) {
                throw new EventPublicationException("kafka fora", new RuntimeException("boom"));
            }
            // o recebível já tem de estar gravado quando o evento sai
            storedAtPublish.add(store.byPayment.containsKey(r.getPaymentId()));
            published.add(r);
        }
    }

    FakeStore store;
    FakePublisher publisher;
    ReceivableService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.seedIfAbsent(List.of(
                new Merchant("X", "X", MerchantStatus.ACTIVE),
                new Merchant("Y", "Y", MerchantStatus.INACTIVE)));
        publisher = new FakePublisher();
        publisher.store = store;
        service = new ReceivableService(store, publisher);
    }

    @Test
    void createsReceivableAndPublishesAfterItIsStored() {
        UUID paymentId = UUID.randomUUID();

        ReceivableService.Result result = service.register(paymentId, "X", 100);

        assertThat(result.created()).isTrue();
        assertThat(result.receivable().getPaymentId()).isEqualTo(paymentId);
        assertThat(result.receivable().getMerchantId()).isEqualTo("X");
        assertThat(result.receivable().getAmount()).isEqualTo(100);
        assertThat(result.receivable().isEventPublished()).isTrue();
        assertThat(store.byPayment).hasSize(1);
        assertThat(store.byPayment.get(paymentId).isEventPublished()).isTrue();
        assertThat(publisher.published).hasSize(1);
        assertThat(publisher.storedAtPublish).containsExactly(true);
    }

    @Test
    void duplicatePaymentIdIsIdempotentAndDoesNotRepublish() {
        UUID paymentId = UUID.randomUUID();
        ReceivableService.Result first = service.register(paymentId, "X", 100);

        ReceivableService.Result second = service.register(paymentId, "X", 100);

        assertThat(second.created()).isFalse();
        assertThat(second.receivable().getId()).isEqualTo(first.receivable().getId());
        assertThat(store.byPayment).hasSize(1);
        assertThat(publisher.published).hasSize(1);
    }

    @Test
    void samePaymentIdWithDifferentDataIsConflict() {
        UUID paymentId = UUID.randomUUID();
        service.register(paymentId, "X", 100);

        assertThatThrownBy(() -> service.register(paymentId, "X", 999))
                .isInstanceOf(PaymentConflictException.class)
                .extracting(e -> ((DomainException) e).getStatus()).isEqualTo(409);
    }

    @Test
    void unknownMerchantIs404AndNothingIsStoredOrPublished() {
        assertThatThrownBy(() -> service.register(UUID.randomUUID(), "Z", 10))
                .isInstanceOf(MerchantNotFoundException.class)
                .extracting(e -> ((DomainException) e).getStatus()).isEqualTo(404);
        assertThat(store.byPayment).isEmpty();
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void inactiveMerchantIs409AndNothingIsStoredOrPublished() {
        assertThatThrownBy(() -> service.register(UUID.randomUUID(), "Y", 10))
                .isInstanceOf(MerchantInactiveException.class)
                .extracting(e -> ((DomainException) e).getStatus()).isEqualTo(409);
        assertThat(store.byPayment).isEmpty();
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void publishFailureKeepsReceivableUnpublishedAndSignals503() {
        UUID paymentId = UUID.randomUUID();
        publisher.fail = true;

        assertThatThrownBy(() -> service.register(paymentId, "X", 100))
                .isInstanceOf(EventNotPublishedException.class)
                .extracting(e -> ((DomainException) e).getStatus()).isEqualTo(503);

        assertThat(store.byPayment).hasSize(1);
        assertThat(store.byPayment.get(paymentId).isEventPublished()).isFalse();
    }

    @Test
    void retryRepublishesWhenEventWasNotPublishedKeepingEventId() {
        UUID paymentId = UUID.randomUUID();
        publisher.fail = true;
        assertThatThrownBy(() -> service.register(paymentId, "X", 100))
                .isInstanceOf(EventNotPublishedException.class);
        UUID storedEventId = store.byPayment.get(paymentId).getEventId();
        publisher.fail = false;

        ReceivableService.Result retry = service.register(paymentId, "X", 100);

        assertThat(retry.created()).isFalse();
        assertThat(retry.receivable().isEventPublished()).isTrue();
        assertThat(store.byPayment).hasSize(1);
        assertThat(store.byPayment.get(paymentId).isEventPublished()).isTrue();
        assertThat(publisher.published).hasSize(1);
        assertThat(publisher.published.get(0).getEventId()).isEqualTo(storedEventId);

        // um terceiro pedido não republica mais
        service.register(paymentId, "X", 100);
        assertThat(publisher.published).hasSize(1);
    }

    @Test
    void raceOnSamePaymentIdReturnsExistingWithoutPublishingTwice() {
        UUID paymentId = UUID.randomUUID();
        store.raceWinner = Receivable.builder()
                .id(UUID.randomUUID()).paymentId(paymentId).merchantId("X").amount(100)
                .createdAt(java.time.Instant.now()).eventPublished(false).eventId(UUID.randomUUID()).build();

        ReceivableService.Result result = service.register(paymentId, "X", 100);

        assertThat(result.created()).isFalse();
        assertThat(result.receivable().getId()).isEqualTo(store.byPayment.get(paymentId).getId());
        assertThat(store.byPayment).hasSize(1);
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void listByMerchantRequiresKnownMerchant() {
        UUID paymentId = UUID.randomUUID();
        service.register(paymentId, "X", 5);

        assertThat(service.listByMerchant("X")).extracting(Receivable::getPaymentId).containsExactly(paymentId);
        assertThatThrownBy(() -> service.listByMerchant("Z")).isInstanceOf(MerchantNotFoundException.class);
    }
}
