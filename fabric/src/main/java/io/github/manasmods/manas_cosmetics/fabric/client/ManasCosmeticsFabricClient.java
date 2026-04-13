package io.github.manasmods.manas_cosmetics.fabric.client;

import io.github.manasmods.manas_cosmetics.client.ManasCosmticsClient;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer;
import io.github.manasmods.manas_cosmetics.client.renderer.PetCosmeticRenderer;
import io.github.manasmods.manas_cosmetics.entity.EntityRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

public final class ManasCosmeticsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ManasCosmticsClient.init();

        // Attach CosmeticLayer to the player renderer so equipped cosmetics are drawn
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
            (entityType, renderer, registrationHelper, context) -> {
                if (renderer instanceof PlayerRenderer playerRenderer) {
                    registrationHelper.register(
                        new CosmeticLayer<>(playerRenderer, ClientCosmeticModelCache.get())
                    );
                }
            }
        );

        // Register the pet entity renderer so pet cosmetics are visible in the world
        EntityRendererRegistry.register(EntityRegistry.PET_COSMETIC.get(), PetCosmeticRenderer::new);
    }
}
