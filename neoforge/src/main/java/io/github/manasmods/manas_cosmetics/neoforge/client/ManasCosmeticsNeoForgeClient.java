package io.github.manasmods.manas_cosmetics.neoforge.client;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only NeoForge event subscriber.
 *
 * Using {@code @EventBusSubscriber(value = Dist.CLIENT)} ensures NeoForge only
 * loads and registers this class on the client, so no client-only classes are
 * touched on a dedicated server.
 */
@EventBusSubscriber(modid = ManasCosmetics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ManasCosmeticsNeoForgeClient {

    private ManasCosmeticsNeoForgeClient() {}

    /**
     * Attaches {@link CosmeticLayer} to both default and slim player renderers.
     *
     * {@code getSkins()} returns {@code Set<PlayerSkin.Model>}; use {@code getSkin(model)}
     * to retrieve each renderer, then cast to {@link PlayerRenderer} before calling {@code addLayer()}.
     */
    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        event.getSkins().forEach(skin -> {
            var renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new CosmeticLayer<>(playerRenderer, ClientCosmeticModelCache.get()));
            }
        });
    }
}