package io.github.manasmods.manas_cosmetics.neoforge;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.entity.EntityRegistry;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(ManasCosmetics.MOD_ID)
public final class ManasCosmeticsNeoForge {

    public ManasCosmeticsNeoForge(IEventBus modBus) {
        ManasCosmetics.init();

        // Register entity attribute defaults (both sides)
        modBus.addListener(ManasCosmeticsNeoForge::registerAttributes);

        // Client-only init: keybindings, S2C packets, etc.
        // Java lazy class loading means ManasCosmticsClient is never loaded on a
        // dedicated server because this branch never executes there.
        // Render layer registration lives in ManasCosmeticsNeoForgeClient, which
        // is guarded by @EventBusSubscriber(value = Dist.CLIENT).
        if (FMLEnvironment.dist == Dist.CLIENT) {
            io.github.manasmods.manas_cosmetics.client.ManasCosmticsClient.init();
        }
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(EntityRegistry.PET_COSMETIC.get(), PetCosmeticEntity.createAttributes().build());
    }
}
