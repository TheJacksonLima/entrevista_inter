package co.inter.piggies.reserve;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake em memória com a mesma semântica das operações atômicas do Postgres (apenas para testes unitários). */
class InMemoryReserveStore implements ReserveStore {

    final Map<String, long[]> accounts = new HashMap<>(); // [available, reserved]
    final Map<UUID, Reservation> reservations = new HashMap<>();

    long available(String clientId) {
        return accounts.get(clientId)[0];
    }

    long reserved(String clientId) {
        return accounts.get(clientId)[1];
    }

    @Override
    public boolean insertPending(UUID paymentId, String clientId, long amount) {
        if (reservations.containsKey(paymentId)) {
            return false;
        }
        reservations.put(paymentId, new Reservation(paymentId, clientId, amount, ReserveStatus.PENDING));
        return true;
    }

    @Override
    public boolean moveAvailableToReserved(String clientId, long amount) {
        long[] a = accounts.get(clientId);
        if (a == null || a[0] < amount) {
            return false;
        }
        a[0] -= amount;
        a[1] += amount;
        return true;
    }

    @Override
    public Optional<Reservation> find(UUID paymentId) {
        return Optional.ofNullable(reservations.get(paymentId));
    }

    @Override
    public boolean transition(UUID paymentId, ReserveStatus from, ReserveStatus to) {
        Reservation r = reservations.get(paymentId);
        if (r == null || r.status() != from) {
            return false;
        }
        reservations.put(paymentId, r.withStatus(to));
        return true;
    }

    @Override
    public boolean debitReserved(String clientId, long amount) {
        long[] a = accounts.get(clientId);
        if (a == null || a[1] < amount) {
            return false;
        }
        a[1] -= amount;
        return true;
    }

    @Override
    public boolean returnReservedToAvailable(String clientId, long amount) {
        long[] a = accounts.get(clientId);
        if (a == null || a[1] < amount) {
            return false;
        }
        a[1] -= amount;
        a[0] += amount;
        return true;
    }

    @Override
    public void createAccountIfAbsent(String clientId, long available) {
        accounts.putIfAbsent(clientId, new long[]{available, 0});
    }
}
