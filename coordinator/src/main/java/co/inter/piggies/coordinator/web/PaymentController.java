package co.inter.piggies.coordinator.web;

import co.inter.piggies.coordinator.domain.Payment;
import co.inter.piggies.coordinator.port.PaymentOrchestrator;
import co.inter.piggies.coordinator.service.PaymentService;
import co.inter.piggies.coordinator.service.PaymentService.Registration;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.UUID;

@Controller("/payments")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
public class PaymentController {

    private final PaymentService service;
    private final PaymentOrchestrator orchestrator;

    public PaymentController(PaymentService service, PaymentOrchestrator orchestrator) {
        this.service = service;
        this.orchestrator = orchestrator;
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<PaymentAccepted> create(
            @Valid @Body PaymentRequest request,
            @Nullable @Header("Idempotency-Key") @Size(max = 100) String idempotencyKey) {
        // O registro PENDING já está commitado quando register() retorna.
        Registration reg = service.register(request.clientId(), request.merchantId(), request.amount(), idempotencyKey);
        Payment payment = reg.payment();
        if (reg.created()) {
            orchestrator.submit(payment.toIntent()); // assíncrono; resultado chega via PaymentStatusUpdater
        }
        return HttpResponse.<PaymentAccepted>accepted(URI.create("/payments/" + payment.getId()))
                .body(new PaymentAccepted(payment.getId(), payment.getStatus()));
    }

    @Get(value = "/{paymentId}", produces = {MediaType.APPLICATION_JSON, "application/problem+json"})
    public HttpResponse<?> get(UUID paymentId) {
        return service.find(paymentId)
                .<HttpResponse<?>>map(p -> HttpResponse.ok(PaymentStatusResponse.from(p)))
                .orElseGet(() -> HttpResponse.status(HttpStatus.NOT_FOUND)
                        .contentType("application/problem+json")
                        .body(new ProblemBody("about:blank", "Not Found", 404,
                                "Pagamento " + paymentId + " não encontrado")));
    }
}
