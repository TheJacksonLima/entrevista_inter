package co.inter.piggies.coordinator.service;

import co.inter.piggies.coordinator.config.OrchestratorProperties;
import co.inter.piggies.coordinator.domain.PaymentIntent;
import co.inter.piggies.coordinator.domain.RejectionReason;
import co.inter.piggies.coordinator.gateway.MerchantGateway;
import co.inter.piggies.coordinator.gateway.MerchantGateway.MerchantResult;
import co.inter.piggies.coordinator.gateway.ReserveGateway;
import co.inter.piggies.coordinator.gateway.ReserveGateway.ReserveResult;
import co.inter.piggies.coordinator.port.PaymentOrchestrator;
import co.inter.piggies.coordinator.port.PaymentStatusUpdater;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Orquestração do pagamento (docs/diagramas.md):
 *
 * <ol>
 *   <li>reserva de saldo e validação do merchant em <b>paralelo</b>, cada uma com timeout;</li>
 *   <li>espera as duas terminarem para decidir com o quadro completo;</li>
 *   <li>tudo OK → confirma o débito → registra o crédito → {@code CONFIRMED};</li>
 *   <li>alguma falha → libera a reserva (se pode existir) → {@code REJECTED} com o motivo.</li>
 * </ol>
 *
 * <p>Depois que o débito é confirmado não há como voltar atrás (sem cancelamento pós-confirmação).
 * Se o crédito não puder ser registrado, o pagamento fica {@code PENDING} e o erro é logado para
 * reprocessamento — nunca é marcado {@code REJECTED} com o cliente já debitado.
 */
@Singleton
public class DefaultPaymentOrchestrator implements PaymentOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPaymentOrchestrator.class);

    private final ReserveGateway reserve;
    private final MerchantGateway merchant;
    private final PaymentStatusUpdater status;
    private final ExecutorService executor;
    private final OrchestratorProperties properties;

    public DefaultPaymentOrchestrator(ReserveGateway reserve,
                                      MerchantGateway merchant,
                                      PaymentStatusUpdater status,
                                      @Named(TaskExecutors.BLOCKING) ExecutorService executor,
                                      OrchestratorProperties properties) {
        this.reserve = reserve;
        this.merchant = merchant;
        this.status = status;
        this.executor = executor;
        this.properties = properties;
    }

    @Override
    public CompletableFuture<Void> submit(PaymentIntent intent) {
        return CompletableFuture.runAsync(() -> runSafely(intent), executor);
    }

    private void runSafely(PaymentIntent intent) {
        try {
            run(intent);
        } catch (RuntimeException e) {
            // Falha inesperada (ex.: ao persistir o status). O pagamento fica PENDING para reprocessamento.
            LOG.error("Erro inesperado ao processar pagamento {}", intent.paymentId(), e);
        }
    }

    private void run(PaymentIntent intent) {
        long timeoutMs = properties.timeout().toMillis();

        CompletableFuture<ReserveResult> reservation = CompletableFuture
                .supplyAsync(() -> reserve.reserve(intent), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(t -> {
                    LOG.warn("Reserva de {} falhou ou expirou: {}", intent.paymentId(), t.toString());
                    return ReserveResult.UNAVAILABLE;
                });

        CompletableFuture<MerchantResult> validation = CompletableFuture
                .supplyAsync(() -> merchant.validate(intent.merchantId()), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(t -> {
                    LOG.warn("Validação do merchant {} falhou ou expirou: {}", intent.merchantId(), t.toString());
                    return MerchantResult.UNAVAILABLE;
                });

        // As duas já tratam suas falhas (exceptionally): join() não lança.
        CompletableFuture.allOf(reservation, validation).join();
        decide(intent, reservation.join(), validation.join());
    }

    private void decide(PaymentIntent intent, ReserveResult reserved, MerchantResult merchantResult) {
        switch (reserved) {
            case INSUFFICIENT_FUNDS -> reject(intent, RejectionReason.INSUFFICIENT_FUNDS);
            case UNAVAILABLE -> {
                // Não sabemos se a reserva chegou a ser criada: libera por segurança (idempotente).
                reserve.release(intent.paymentId());
                reject(intent, RejectionReason.RESERVE_UNAVAILABLE);
            }
            case RESERVED -> {
                if (merchantResult == MerchantResult.ACTIVE) {
                    settle(intent);
                } else {
                    reserve.release(intent.paymentId());
                    reject(intent, merchantResult == MerchantResult.UNAVAILABLE
                            ? RejectionReason.MERCHANT_UNAVAILABLE
                            : RejectionReason.MERCHANT_INVALID);
                }
            }
        }
    }

    /** Reserva + merchant OK: confirma o débito e registra o crédito. */
    private void settle(PaymentIntent intent) {
        if (!retry(() -> reserve.confirm(intent.paymentId()))) {
            LOG.error("Não foi possível confirmar o débito de {}; pagamento permanece PENDING (requer reprocessamento)",
                    intent.paymentId());
            return;
        }
        if (!retry(() -> merchant.credit(intent))) {
            LOG.error("Débito de {} confirmado, mas o crédito ao merchant {} falhou; pagamento permanece PENDING "
                    + "(requer reprocessamento do crédito, que é idempotente)", intent.paymentId(), intent.merchantId());
            return;
        }
        status.markConfirmed(intent.paymentId());
    }

    private void reject(PaymentIntent intent, RejectionReason reason) {
        status.markRejected(intent.paymentId(), reason);
    }

    private boolean retry(BooleanSupplier operation) {
        int attempts = Math.max(1, properties.retryAttempts());
        for (int i = 1; i <= attempts; i++) {
            try {
                if (operation.getAsBoolean()) {
                    return true;
                }
            } catch (RuntimeException e) {
                LOG.warn("Tentativa {}/{} falhou: {}", i, attempts, e.toString());
            }
        }
        return false;
    }
}
