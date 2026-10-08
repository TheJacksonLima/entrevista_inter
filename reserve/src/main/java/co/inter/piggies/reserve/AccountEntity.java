package co.inter.piggies.reserve;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conta de Piggies. Mapeamento usado para gerar o schema; as mutações de saldo são feitas por
 * UPDATEs atômicos em {@link JpaReserveStore}, nunca por read-modify-write da entidade.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class AccountEntity {

    @Id
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "available", nullable = false, columnDefinition = "bigint not null check (available >= 0)")
    private long available;

    @Column(name = "reserved", nullable = false, columnDefinition = "bigint not null check (reserved >= 0)")
    private long reserved;
}
