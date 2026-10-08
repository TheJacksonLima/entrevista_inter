package co.inter.piggies.merchant.messaging;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Payload do evento PagamentoConfirmado, exatamente o de
 * {@code contracts/events/pagamento-confirmado.v1.schema.json} (sem campos extras).
 * UUIDs e instante vão como String para o JSON sair no formato do schema ({@code uuid}, {@code date-time}).
 */
@Serdeable
public record PagamentoConfirmadoEvent(
        String eventId,
        String paymentId,
        String merchantId,
        long amount,
        String occurredAt) {
}
