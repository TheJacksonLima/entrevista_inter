package co.inter.piggies.merchant.repository;

import co.inter.piggies.merchant.domain.Merchant;
import co.inter.piggies.merchant.domain.MerchantStatus;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

import java.util.List;

/** Seed idempotente na subida: X ativo, Y inativo (nunca sobrescreve merchants existentes). */
@Singleton
public class MerchantSeeder {

    private final ReceivableStore store;

    public MerchantSeeder(ReceivableStore store) {
        this.store = store;
    }

    @EventListener
    public void onStartup(StartupEvent event) {
        store.seedIfAbsent(List.of(
                new Merchant("X", "Merchant X (ativo)", MerchantStatus.ACTIVE),
                new Merchant("Y", "Merchant Y (inativo)", MerchantStatus.INACTIVE)));
    }
}
