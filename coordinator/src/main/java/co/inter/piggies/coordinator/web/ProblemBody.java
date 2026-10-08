package co.inter.piggies.coordinator.web;

import io.micronaut.serde.annotation.Serdeable;

/** Corpo RFC 7807 do contrato. */
@Serdeable
public record ProblemBody(String type, String title, int status, String detail) {
}
