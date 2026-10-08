package co.inter.piggies.merchant.service;

import co.inter.piggies.merchant.domain.Merchant;
import co.inter.piggies.merchant.domain.Receivable;
import co.inter.piggies.merchant.messaging.EventPublicationException;
import co.inter.piggies.merchant.messaging.PagamentoConfirmadoPublisher;
import co.inter.piggies.merchant.repository.ReceivableStore;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do crédito do merchant. Não é transacional de propósito: cada operação do {@link ReceivableStore}
 * faz commit sozinha, então a publicação do evento acontece sempre DEPOIS do commit do recebível.
 *
 * <p>Republicação: se o recebível existe com {@code event_published = false} (publicação anterior falhou),
 * um retry do mesmo paymentId republica reutilizando o MESMO {@code eventId} (e o mesmo {@code occurredAt}
 * = {@code created_at}), assim os consumidores podem deduplicar por eventId.
 */
@Singleton
public class ReceivableService {

    public record Result(Receivable receivable, boolean created) {
    }

    private final ReceivableStore store;
    private final PagamentoConfirmadoPublisher publisher;

    public ReceivableService(ReceivableStore store, PagamentoConfirmadoPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    public Merchant getMerchant(String merchantId) {
        return store.findMerchant(merchantId).orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }

    public List<Receivable> listByMerchant(String merchantId) {
        getMerchant(merchantId);
        return store.findByMerchant(merchantId);
    }

    public Result register(UUID paymentId, String merchantId, long amount) {
        Optional<Receivable> existing = store.findByPaymentId(paymentId);
        if (existing.isPresent()) {
            return replay(existing.get(), merchantId, amount, true);
        }

        Merchant merchant = getMerchant(merchantId);
        if (!merchant.isActive()) {
            throw new MerchantInactiveException(merchantId);
        }

        Receivable receivable = Receivable.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .merchantId(merchantId)
                .amount(amount)
                // microssegundos: é a precisão do Postgres, então o valor lido depois é idêntico
                .createdAt(Instant.now().truncatedTo(ChronoUnit.MICROS))
                .eventPublished(false)
                .eventId(UUID.randomUUID())
                .build();

        if (!store.insertIfAbsent(receivable)) {
            // Corrida: outra requisição gravou o mesmo paymentId entre o SELECT e o INSERT.
            // Devolve o recebível dela (200); a publicação fica a cargo de quem gravou (ou de um retry).
            Receivable winner = store.findByPaymentId(paymentId).orElseThrow();
            return replay(winner, merchantId, amount, false);
        }

        publishAndMark(receivable);
        return new Result(receivable, true);
    }

    private Result replay(Receivable existing, String merchantId, long amount, boolean republishIfPending) {
        if (!existing.getMerchantId().equals(merchantId) || existing.getAmount() != amount) {
            throw new PaymentConflictException(existing.getPaymentId());
        }
        if (republishIfPending && !existing.isEventPublished()) {
            publishAndMark(existing);
        }
        return new Result(existing, false);
    }

    private void publishAndMark(Receivable receivable) {
        try {
            publisher.publish(receivable);
        } catch (EventPublicationException e) {
            throw new EventNotPublishedException(receivable.getPaymentId(), e);
        }
        store.markEventPublished(receivable.getId());
        receivable.setEventPublished(true);
    }
}
