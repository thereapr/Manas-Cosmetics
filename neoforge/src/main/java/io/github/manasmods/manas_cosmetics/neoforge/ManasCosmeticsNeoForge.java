package io.github.manasmods.manas_cosmetics.neoforge;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer;
import io.github.manasmods.manas_cosmetics.entity.EntityRegistry;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(ManasCosmetics.MOD_ID)
public final class ManasCosmeticsNeoForge {

    public ManasCosmeticsNeoForge(IEventBus modBus) {
        ManasCosmetics.init();

        // Register entity attribute defaults
        modBus.addListener(ManasCosmeticsNeoForge::registerAttributes);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            io.github.manasmods.manas_cosmetics.client.ManasCosmticsClient.init();
            modBus.addListener(ManasCosmeticsNeoForge::registerRenderLayers);
        }
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(EntityRegistry.PET_COSMETIC.get(), PetCosmeticEntity.createAttributes().build());
    }

    /** Attaches CosmeticLayer to both the default and slim player renderers. */
    @OnlyIn(Dist.CLIENT)
    private static void registerRenderLayers(EntityRenderersEvent.AddLayers event) {
        event.getSkinMap().forEach((skin, playerRenderer) ->
            playerRenderer.addLayer(new CosmeticLayer<>(playerRenderer, ClientCosmeticModelCache.get()))
        );
    }
}
