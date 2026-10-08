package co.inter.piggies.reserve;

import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação PostgreSQL. Todas as mutações são SQL nativo atômico (UPDATE condicional / INSERT ON CONFLICT),
 * executadas na transação aberta pelo serviço.
 */
@Singleton
public class JpaReserveStore implements ReserveStore {

    private final EntityManager em;

    public JpaReserveStore(EntityManager em) {
        this.em = em;
    }

    @Override
    public boolean insertPending(UUID paymentId, String clientId, long amount) {
        int rows = em.createNativeQuery(
                        "INSERT INTO reservation (payment_id, client_id, amount, status, created_at, updated_at) "
                                + "VALUES (:paymentId, :clientId, :amount, 'PENDING', now(), now()) "
                                + "ON CONFLICT (payment_id) DO NOTHING")
                .setParameter("paymentId", paymentId)
                .setParameter("clientId", clientId)
                .setParameter("amount", amount)
                .executeUpdate();
        return rows == 1;
    }

    @Override
    public boolean moveAvailableToReserved(String clientId, long amount) {
        return em.createNativeQuery(
                        "UPDATE account SET available = available - :amount, reserved = reserved + :amount "
                                + "WHERE client_id = :clientId AND available >= :amount")
                .setParameter("amount", amount)
                .setParameter("clientId", clientId)
                .executeUpdate() == 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Reservation> find(UUID paymentId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT payment_id, client_id, amount, status FROM reservation WHERE payment_id = :paymentId")
                .setParameter("paymentId", paymentId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = rows.get(0);
        return Optional.of(new Reservation(
                UUID.fromString(r[0].toString()),
                (String) r[1],
                ((Number) r[2]).longValue(),
                ReserveStatus.valueOf((String) r[3])));
    }

    @Override
    public boolean transition(UUID paymentId, ReserveStatus from, ReserveStatus to) {
        return em.createNativeQuery(
                        "UPDATE reservation SET status = :to, updated_at = now() "
                                + "WHERE payment_id = :paymentId AND status = :from")
                .setParameter("to", to.name())
                .setParameter("from", from.name())
                .setParameter("paymentId", paymentId)
                .executeUpdate() == 1;
    }

    @Override
    public boolean debitReserved(String clientId, long amount) {
        return em.createNativeQuery(
                        "UPDATE account SET reserved = reserved - :amount "
                                + "WHERE client_id = :clientId AND reserved >= :amount")
                .setParameter("amount", amount)
                .setParameter("clientId", clientId)
                .executeUpdate() == 1;
    }

    @Override
    public boolean returnReservedToAvailable(String clientId, long amount) {
        return em.createNativeQuery(
                        "UPDATE account SET reserved = reserved - :amount, available = available + :amount "
                                + "WHERE client_id = :clientId AND reserved >= :amount")
                .setParameter("amount", amount)
                .setParameter("clientId", clientId)
                .executeUpdate() == 1;
    }

    @Override
    public void createAccountIfAbsent(String clientId, long available) {
        em.createNativeQuery(
                        "INSERT INTO account (client_id, available, reserved) VALUES (:clientId, :available, 0) "
                                + "ON CONFLICT (client_id) DO NOTHING")
                .setParameter("clientId", clientId)
                .setParameter("available", available)
                .executeUpdate();
    }
}
