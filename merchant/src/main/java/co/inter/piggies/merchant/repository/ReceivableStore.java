package co.inter.piggies.merchant.repository;

import co.inter.piggies.merchant.domain.Merchant;
import co.inter.piggies.merchant.domain.Receivable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de persistência do serviço de recebíveis (permite testar o serviço sem banco). */
public interface ReceivableStore {

    Optional<Merchant> findMerchant(String merchantId);

    Optional<Receivable> findByPaymentId(UUID paymentId);

    List<Receivable> findByMerchant(String merchantId);

    /**
     * Grava o recebível numa transação própria (commit ao retornar).
     *
     * @return {@code true} se gravou; {@code false} se já existia um recebível com o mesmo
     * {@code paymentId} (corrida / retry) — nesse caso nada é alterado.
     */
    boolean insertIfAbsent(Receivable receivable);

    void markEventPublished(UUID receivableId);

    /** Cria os merchants informados que ainda não existem (nunca sobrescreve). */
    void seedIfAbsent(List<Merchant> merchants);
}
