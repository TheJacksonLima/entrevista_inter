package co.inter.piggies.coordinator.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

/**
 * Configuração do orquestrador ({@code spp.orchestrator.*} no application.yml).
 *
 * @param timeout       tempo máximo de cada etapa paralela (reserva / validação do merchant)
 * @param retryAttempts tentativas para confirmar o débito e registrar o crédito
 */
@ConfigurationProperties("spp.orchestrator")
public record OrchestratorProperties(
        @Bindable(defaultValue = "5s") Duration timeout,
        @Bindable(defaultValue = "3") int retryAttempts) {
}
