package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.domain.Payment;
import io.micronaut.data.exceptions.DataAccessException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake em memória; simula a constraint UNIQUE de idempotency_key. */
class InMemoryPaymentRepository implements PaymentRepository {

    final Map<UUID, Payment> store = new ConcurrentHashMap<>();
    /** Quando preenchido, o próximo findByIdempotencyKey devolve vazio uma única vez (simula a corrida). */
    volatile boolean hideNextLookup;

    @Override
    public synchronized Payment save(Payment payment) {
        String key = payment.getIdempotencyKey();
        if (key != null && store.values().stream().anyMatch(p -> key.equals(p.getIdempotencyKey()))) {
            throw new DataAccessException("duplicate key idempotency_key");
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Payment update(Payment payment) {
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String key) {
        if (hideNextLookup) {
            hideNextLookup = false;
            return Optional.empty();
        }
        return store.values().stream().filter(p -> key.equals(p.getIdempotencyKey())).findFirst();
    }
}
