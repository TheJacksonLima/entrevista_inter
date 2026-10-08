package co.inter.piggies.reserve;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

import java.util.UUID;

@Controller("/reserves")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
public class ReserveController {

    private final ReserveService service;

    public ReserveController(ReserveService service) {
        this.service = service;
    }

    @Post
    public HttpResponse<ReserveResponse> create(@Body @Valid ReserveRequest request) {
        ReserveService.ReserveResult result =
                service.reserve(request.paymentId(), request.clientId(), request.amount());
        ReserveResponse body = ReserveResponse.from(result.reservation());
        return result.created() ? HttpResponse.created(body) : HttpResponse.ok(body);
    }

    @Get("/{paymentId}")
    public ReserveResponse get(@PathVariable UUID paymentId) {
        return ReserveResponse.from(service.get(paymentId));
    }

    @Post("/{paymentId}/confirm")
    public ReserveResponse confirm(@PathVariable UUID paymentId) {
        return ReserveResponse.from(service.confirm(paymentId));
    }

    @Post("/{paymentId}/release")
    public ReserveResponse release(@PathVariable UUID paymentId) {
        return ReserveResponse.from(service.release(paymentId));
    }
}
