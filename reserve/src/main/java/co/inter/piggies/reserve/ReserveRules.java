package co.inter.piggies.reserve;

/** Regras puras de transição de estado (testáveis sem banco). */
public final class ReserveRules {

    /** O que fazer diante de um pedido de transição. */
    public enum Action {
        /** Aplicar a transição (estado atual é PENDING). */
        APPLY,
        /** Já está no estado alvo: sucesso idempotente, nada a fazer. */
        NOOP,
        /** Estado terminal oposto: conflito (409). */
        CONFLICT
    }

    private ReserveRules() {
    }

    public static Action confirm(ReserveStatus current) {
        return switch (current) {
            case PENDING -> Action.APPLY;
            case CONFIRMED -> Action.NOOP;
            case RELEASED -> Action.CONFLICT;
        };
    }

    public static Action release(ReserveStatus current) {
        return switch (current) {
            case PENDING -> Action.APPLY;
            case RELEASED -> Action.NOOP;
            case CONFIRMED -> Action.CONFLICT;
        };
    }
}
