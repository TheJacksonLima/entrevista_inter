package co.inter.piggies.coordinator.client;

import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.gateway.MerchantGateway;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chama o PiggiesMerchantService por HTTP síncrono (a URL vem de
 * {@code micronaut.http.services.merchant.url}). Nunca propaga exceção HTTP ao orquestrador.
 */
@Singleton
public class HttpMerchantGateway implements MerchantGateway {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMerchantGateway.class);

    private final BlockingHttpClient http;

    public HttpMerchantGateway(@Client("merchant") HttpClient http) {
        this.http = http.toBlocking();
    }

    @Override
    public MerchantResult validate(String merchantId) {
        try {
            var uri = UriBuilder.of("/merchants").path(merchantId).path("status").build();
            MerchantStatusResponse body = http.retrieve(HttpRequest.GET(uri), MerchantStatusResponse.class);
            return body.active() ? MerchantResult.ACTIVE : MerchantResult.INACTIVE;
        } catch (HttpClientResponseException e) {
            if (e.getStatus() == HttpStatus.NOT_FOUND) {
                return MerchantResult.NOT_FOUND;
            }
            LOG.warn("Merchant respondeu {} ao validar {}", e.getStatus(), merchantId);
            return MerchantResult.UNAVAILABLE;
        } catch (HttpClientException e) {
            LOG.warn("Falha ao validar merchant {}: {}", merchantId, e.getMessage());
            return MerchantResult.UNAVAILABLE;
        }
    }

    @Override
    public boolean credit(PaymentIntent intent) {
        try {
            http.exchange(HttpRequest.POST("/receivables",
                    new ReceivableRequest(intent.paymentId(), intent.merchantId(), intent.amount())));
            return true;
        } catch (HttpClientException e) {
            LOG.warn("Falha ao registrar crédito de {}: {}", intent.paymentId(), e.getMessage());
            return false;
        }
    }
}
