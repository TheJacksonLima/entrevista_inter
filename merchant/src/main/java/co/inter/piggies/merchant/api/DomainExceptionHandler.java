package co.inter.piggies.merchant.api;

import co.inter.piggies.merchant.service.DomainException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/** Erros de negócio em application/problem+json. */
@Singleton
public class DomainExceptionHandler implements ExceptionHandler<DomainException, HttpResponse<Dtos.Problem>> {

    @Override
    public HttpResponse<Dtos.Problem> handle(HttpRequest request, DomainException e) {
        Dtos.Problem problem = new Dtos.Problem("about:blank", e.getTitle(), e.getStatus(), e.getMessage());
        return HttpResponse.<Dtos.Problem>status(HttpStatus.valueOf(e.getStatus()))
                .contentType("application/problem+json")
                .body(problem);
    }
}
