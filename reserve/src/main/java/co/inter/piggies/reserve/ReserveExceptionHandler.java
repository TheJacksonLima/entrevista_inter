package co.inter.piggies.reserve;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

/** Traduz as exceções de negócio em application/problem+json (RFC 7807), conforme o contrato. */
@Singleton
public class ReserveExceptionHandler implements ExceptionHandler<ReserveException, HttpResponse<ReserveExceptionHandler.Problem>> {

    public static final String PROBLEM_JSON = "application/problem+json";

    @Serdeable
    public record Problem(String type, String title, int status, String detail) {
    }

    @Override
    public HttpResponse<Problem> handle(HttpRequest request, ReserveException e) {
        HttpStatus status;
        String type;
        String title;
        if (e instanceof ReserveException.NotFound) {
            status = HttpStatus.NOT_FOUND;
            type = "urn:spp:reserve:not-found";
            title = "Reserva não encontrada";
        } else if (e instanceof ReserveException.InsufficientBalance) {
            status = HttpStatus.CONFLICT;
            type = "urn:spp:reserve:insufficient-balance";
            title = "Saldo insuficiente";
        } else {
            status = HttpStatus.CONFLICT;
            type = "urn:spp:reserve:invalid-state";
            title = "Transição de estado inválida";
        }
        return HttpResponse.<Problem>status(status)
                .contentType(PROBLEM_JSON)
                .body(new Problem(type, title, status.getCode(), e.getMessage()));
    }
}
