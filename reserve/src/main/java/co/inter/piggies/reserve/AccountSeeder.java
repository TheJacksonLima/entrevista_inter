package co.inter.piggies.reserve;

import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.util.Map;

/** Seed idempotente de contas de demo/teste (A=1000, B=50, POOR=0). Nunca sobrescreve saldos existentes. */
@Singleton
public class AccountSeeder {

    static final Map<String, Long> SEED = Map.of("A", 1000L, "B", 50L, "POOR", 0L);

    private final ReserveStore store;

    public AccountSeeder(ReserveStore store) {
        this.store = store;
    }

    @EventListener
    @Transactional
    public void onStartup(StartupEvent event) {
        SEED.forEach(store::createAccountIfAbsent);
    }

    /** Útil para testes/demo: cria a conta se ainda não existir. */
    @Transactional
    public void ensureAccount(String clientId, long available) {
        store.createAccountIfAbsent(clientId, available);
    }
}
