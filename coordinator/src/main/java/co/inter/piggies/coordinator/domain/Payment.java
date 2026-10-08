package co.inter.piggies.coordinator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pagamento registrado pela borda. O id é o paymentId devolvido ao App. */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private RejectionReason failureReason;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Payment pending(UUID id, String clientId, String merchantId, long amount,
                                  String idempotencyKey, Instant now) {
        Payment p = new Payment();
        p.id = id;
        p.clientId = clientId;
        p.merchantId = merchantId;
        p.amount = amount;
        p.status = PaymentStatus.PENDING;
        p.idempotencyKey = idempotencyKey;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public PaymentIntent toIntent() {
        return new PaymentIntent(id, clientId, merchantId, amount);
    }
}
