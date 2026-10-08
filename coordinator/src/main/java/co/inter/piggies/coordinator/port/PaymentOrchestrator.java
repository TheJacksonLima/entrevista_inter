package co.inter.piggies.coordinator.port;

import co.inter.piggies.coordinator.domain.PaymentIntent;

import java.util.concurrent.CompletableFuture;

/**
 * Orquestra o pagamento de ponta a ponta depois que a intenção foi registrada.
 *
 * <p>Chamado pela borda (Dev 1) logo após gravar {@code PENDING} e responder {@code 202}.
 * A execução é assíncrona e <b>nunca lança exceção</b>: o resultado é comunicado por meio de
 * {@link PaymentStatusUpdater}. O {@code CompletableFuture} existe para testes e para quem
 * quiser aguardar o término.
 */
public interface PaymentOrchestrator {

    CompletableFuture<Void> submit(PaymentIntent intent);
}
