package io.github.manasmods.manas_cosmetics.fabric.client;

import io.github.manasmods.manas_cosmetics.client.ManasCosmticsClient;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer;
import net.fabricmc.api.ClientModInitializer;
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
    }
}
