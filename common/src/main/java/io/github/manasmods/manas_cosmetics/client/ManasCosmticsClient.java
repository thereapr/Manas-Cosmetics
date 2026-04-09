package io.github.manasmods.manas_cosmetics.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.client.gui.WardrobeScreen;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.network.SyncPlayerCosmeticsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side initialisation:
 *  - Registers S2C packet receivers
 *  - Clears client state on disconnect
 *  - Registers the Left-Alt keybinding to open the wardrobe
 */
public final class ManasCosmticsClient {

    private ManasCosmticsClient() {}

    public static void init() {
        registerS2CPackets();

        // Clear cosmetic state when the player leaves a server
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClientCosmeticState.get().reset();
            ClientCosmeticModelCache.get().clear();
        });

        ManasCosmetics.LOGGER.info("[manas_cosmetics] Client init complete.");
    }

    private static void registerS2CPackets() {
        // Receive full player cosmetics sync
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            SyncPlayerCosmeticsPayload.ID,
            (buf, ctx) -> {
                SyncPlayerCosmeticsPayload payload = SyncPlayerCosmeticsPayload.decode(buf);
                ctx.queue(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return;
                    ClientCosmeticState.get().handleSync(payload, mc.player.getUUID());
                });
            }
        );

        // Receive open-wardrobe signal from server (/manas_cosmetics wardrobe command)
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "open_wardrobe_s2c"),
            (buf, ctx) -> {
                ctx.queue(() -> Minecraft.getInstance().setScreen(new WardrobeScreen()));
            }
        );
    }
}
