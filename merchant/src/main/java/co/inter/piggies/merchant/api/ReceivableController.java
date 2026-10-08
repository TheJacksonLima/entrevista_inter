package co.inter.piggies.merchant.api;

import co.inter.piggies.merchant.api.Dtos.ReceivableRequest;
import co.inter.piggies.merchant.api.Dtos.ReceivableResponse;
import co.inter.piggies.merchant.service.ReceivableService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

@Controller("/receivables")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
public class ReceivableController {

    private final ReceivableService service;

    public ReceivableController(ReceivableService service) {
        this.service = service;
    }

    /** 201 quando cria; 200 quando o paymentId já havia sido creditado (idempotente). */
    @Post
    public HttpResponse<ReceivableResponse> create(@Body @Valid ReceivableRequest request) {
        ReceivableService.Result result = service.register(
                request.paymentId(), request.merchantId(), request.amount());
        ReceivableResponse body = ReceivableResponse.from(result.receivable());
        return result.created() ? HttpResponse.created(body) : HttpResponse.ok(body);
    }
}
