package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.domain.Payment;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

import java.util.Optional;
import java.util.UUID;

/** Repositório mínimo (facilita fakes em testes unitários). Cada método roda em sua própria transação. */
@Repository
public interface PaymentRepository extends GenericRepository<Payment, UUID> {

    Payment save(Payment payment);

    Payment update(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
