package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.gateway.ReserveGateway;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Chama o PiggiesReserveService por HTTP síncrono (a URL vem de
 * {@code micronaut.http.services.reserve.url}). Traduz status e falhas de rede para
 * {@link ReserveResult}; nunca propaga exceção HTTP ao orquestrador.
 */
@Singleton
public class HttpReserveGateway implements ReserveGateway {

    private static final Logger LOG = LoggerFactory.getLogger(HttpReserveGateway.class);

    private final BlockingHttpClient http;

    public HttpReserveGateway(@Client("reserve") HttpClient http) {
        this.http = http.toBlocking();
    }

    @Override
    public ReserveResult reserve(PaymentIntent intent) {
        try {
            http.exchange(HttpRequest.POST("/reserves",
                    new ReserveRequest(intent.paymentId(), intent.clientId(), intent.amount())));
            return ReserveResult.RESERVED;
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.CONFLICT) {
                return ReserveResult.INSUFFICIENT_FUNDS;
            }
            LOG.warn("Reserve respondeu {} ao reservar {}", e.getStatus(), intent.paymentId());
            return ReserveResult.UNAVAILABLE;
        } catch (HttpClientException e) {
            LOG.warn("Falha ao reservar {}: {}", intent.paymentId(), e.getMessage());
            return ReserveResult.UNAVAILABLE;
        }
    }

    @Override
    public boolean confirm(UUID paymentId) {
        try {
            http.exchange(HttpRequest.POST("/reserves/" + paymentId + "/confirm", null));
            return true;
        } catch (HttpClientException e) {
            LOG.warn("Falha ao confirmar débito de {}: {}", paymentId, e.getMessage());
            return false;
        }
    }

    @Override
    public void release(UUID paymentId) {
        try {
            http.exchange(HttpRequest.POST("/reserves/" + paymentId + "/release", null));
        } catch (HttpClientException e) {
            // 404 = nunca reservou; 409 = já confirmada. Em ambos nada a compensar.
            LOG.debug("Release de {} sem efeito: {}", paymentId, e.getMessage());
        }
    }
}
