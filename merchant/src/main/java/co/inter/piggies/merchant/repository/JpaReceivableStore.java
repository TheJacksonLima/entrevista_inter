package co.inter.piggies.merchant.repository;

import co.inter.piggies.merchant.domain.Merchant;
import co.inter.piggies.merchant.domain.Receivable;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JPA. Cada método roda na sua própria transação (commit ao retornar), o que garante que
 * o evento só é publicado depois do commit do recebível.
 *
 * <p>O INSERT usa {@code ON CONFLICT DO NOTHING} (PostgreSQL) sobre a UNIQUE de {@code payment_id}: a
 * corrida entre duas requisições com o mesmo paymentId vira {@code false} em vez de exceção/rollback.
 */
@Singleton
public class JpaReceivableStore implements ReceivableStore {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public Optional<Merchant> findMerchant(String merchantId) {
        return Optional.ofNullable(em.find(Merchant.class, merchantId));
    }

    @Override
    @Transactional
    public Optional<Receivable> findByPaymentId(UUID paymentId) {
        return em.createQuery("select r from Receivable r where r.paymentId = :paymentId", Receivable.class)
                .setParameter("paymentId", paymentId)
                .getResultList().stream().findFirst();
    }

    @Override
    @Transactional
    public List<Receivable> findByMerchant(String merchantId) {
        return em.createQuery(
                        "select r from Receivable r where r.merchantId = :merchantId order by r.createdAt, r.id",
                        Receivable.class)
                .setParameter("merchantId", merchantId)
                .getResultList();
    }

    @Override
    @Transactional
    public boolean insertIfAbsent(Receivable r) {
        int rows = em.createNativeQuery(
                        "insert into receivable (id, payment_id, merchant_id, amount, created_at, event_published, event_id) "
                                + "values (:id, :paymentId, :merchantId, :amount, :createdAt, :eventPublished, :eventId) "
                                + "on conflict (payment_id) do nothing")
                .setParameter("id", r.getId())
                .setParameter("paymentId", r.getPaymentId())
                .setParameter("merchantId", r.getMerchantId())
                .setParameter("amount", r.getAmount())
                .setParameter("createdAt", r.getCreatedAt())
                .setParameter("eventPublished", r.isEventPublished())
                .setParameter("eventId", r.getEventId())
                .executeUpdate();
        return rows == 1;
    }

    @Override
    @Transactional
    public void markEventPublished(UUID receivableId) {
        em.createQuery("update Receivable r set r.eventPublished = true where r.id = :id")
                .setParameter("id", receivableId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void seedIfAbsent(List<Merchant> merchants) {
        for (Merchant m : merchants) {
            em.createNativeQuery("insert into merchant (id, name, status) values (:id, :name, :status) "
                            + "on conflict (id) do nothing")
                    .setParameter("id", m.getId())
                    .setParameter("name", m.getName())
                    .setParameter("status", m.getStatus().name())
                    .executeUpdate();
        }
    }
}
