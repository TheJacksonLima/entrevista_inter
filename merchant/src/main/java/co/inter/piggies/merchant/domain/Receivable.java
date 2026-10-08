package co.inter.piggies.merchant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Recebível (crédito) do merchant. {@code payment_id} é UNIQUE: garante a idempotência do crédito.
 * {@code event_id} guarda o identificador do evento PagamentoConfirmado para que uma republicação
 * (quando {@code event_published = false}) reutilize o mesmo eventId e os consumidores possam deduplicar.
 */
@Entity
@Table(name = "receivable")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receivable {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    /** Mapeamento da FK para o merchant; o valor em si é lido por {@link #merchantId}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "merchant_id", insertable = false, updatable = false)
    private String merchantId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "event_published", nullable = false)
    private boolean eventPublished;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;
}
