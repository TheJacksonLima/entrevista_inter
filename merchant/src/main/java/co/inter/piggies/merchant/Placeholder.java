package co.inter.piggies.merchant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * PLACEHOLDER — o Micronaut/Hibernate não inicializa sem nenhuma {@code @Entity}. Esta classe existe só
 * para o serviço subir desde o primeiro minuto. <b>Apague-a</b> (junto com a tabela {@code spp_placeholder})
 * quando criar a primeira entidade real do módulo.
 */
@Entity
@Table(name = "spp_placeholder")
public class Placeholder {

    @Id
    private Long id;
}
