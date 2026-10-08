package co.inter.piggies.reserve;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva por pagamento; o PK {@code payment_id} é a chave de idempotência. Mapeamento usado para
 * gerar o schema; escrita via SQL nativo em {@link JpaReserveStore}.
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class ReservationEntity {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
