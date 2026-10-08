package co.inter.piggies.reserve;

import java.util.UUID;

/** Exceções de negócio do serviço de reservas (RuntimeException => rollback da transação). */
public abstract class ReserveException extends RuntimeException {

    protected ReserveException(String message) {
        super(message);
    }

    /** Saldo disponível insuficiente (ou conta inexistente). */
    public static final class InsufficientBalance extends ReserveException {
        public InsufficientBalance(String clientId, long amount) {
            super("Saldo insuficiente para reservar " + amount + " Piggies do cliente " + clientId);
        }
    }

    /** Reserva inexistente. */
    public static final class NotFound extends ReserveException {
        public NotFound(UUID paymentId) {
            super("Reserva não encontrada para o pagamento " + paymentId);
        }
    }

    /** Transição inválida (ex.: confirmar RELEASED, liberar CONFIRMED). */
    public static final class InvalidState extends ReserveException {
        public InvalidState(UUID paymentId, ReserveStatus current, String attempted) {
            super("Não é possível " + attempted + " a reserva " + paymentId + " no estado " + current);
        }
    }
}
