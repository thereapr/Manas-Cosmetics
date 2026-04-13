package io.github.manasmods.manas_cosmetics.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.client.gui.WardrobeScreen;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.network.SyncCosmeticRegistryPayload;
import io.github.manasmods.manas_cosmetics.network.SyncPlayerCosmeticsPayload;
import io.github.manasmods.manas_cosmetics.network.SyncPresetsPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side initialisation:
 *  - Left-Alt keybinding to open the wardrobe
 *  - S2C packet receivers (cosmetic sync + registry sync + wardrobe open signal)
 *  - State cleanup on disconnect
 */
public final class ManasCosmticsClient {

    /** Left Alt key — configurable in Controls settings. */
    public static final KeyMapping WARDROBE_KEY = new KeyMapping(
        "key.manas_cosmetics.open_wardrobe",
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.manas_cosmetics"
    );

    /** S2C payload for the server-triggered wardrobe open signal. */
    private record OpenWardrobeS2CPayload() implements CustomPacketPayload {
        static final CustomPacketPayload.Type<OpenWardrobeS2CPayload> TYPE =
            new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "open_wardrobe_s2c"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobeS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {}, buf -> new OpenWardrobeS2CPayload());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private ManasCosmticsClient() {}

    public static void init() {
        // Register keybinding
        KeyMappingRegistry.register(WARDROBE_KEY);

        // Poll the keybinding each client tick
        ClientTickEvent.CLIENT_PRE.register(mc -> {
            while (WARDROBE_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new WardrobeScreen());
                }
            }
        });

        registerS2CPackets();

        // Clear cosmetic and GUI state when the player leaves a server
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ClientCosmeticState.get().reset();
            ClientCosmeticModelCache.get().clear();
            WardrobeScreen.clearSavedState();
        });

        ManasCosmetics.LOGGER.info("[manas_cosmetics] Client init complete.");
    }

    private static void registerS2CPackets() {
        // ── S2C: sync a player's equipped cosmetics ────────────────────────────
        NetworkManager.registerReceiver(
            SyncPlayerCosmeticsPayload.TYPE,
            SyncPlayerCosmeticsPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.queue(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                ClientCosmeticState.get().handleSync(payload, mc.player.getUUID());
            })
        );

        // ── S2C: sync the full cosmetic registry (definitions + models) ────────
        NetworkManager.registerReceiver(
            SyncCosmeticRegistryPayload.TYPE,
            SyncCosmeticRegistryPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.queue(() -> {
                ClientCosmeticModelCache cache = ClientCosmeticModelCache.get();
                cache.clear();

                List<io.github.manasmods.manas_cosmetics.api.CosmeticDefinition> defs = new ArrayList<>();
                for (SyncCosmeticRegistryPayload.Entry e : payload.getEntries()) {
                    var model = SyncCosmeticRegistryPayload.deserialiseBBModel(e.bbModelJson());
                    cache.register(e.definition(), model);
                    defs.add(e.definition());
                }
                ClientCosmeticState.get().setAvailableCosmetics(defs);
            })
        );

        // ── S2C: server signals the client to open the wardrobe ───────────────
        NetworkManager.registerReceiver(
            OpenWardrobeS2CPayload.TYPE,
            OpenWardrobeS2CPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.queue(() -> Minecraft.getInstance().setScreen(new WardrobeScreen()))
        );

        // ── S2C: sync the player's saved presets (sent on login) ──────────────
        NetworkManager.registerReceiver(
            SyncPresetsPayload.TYPE,
            SyncPresetsPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.queue(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                ClientCosmeticState.get().handlePresetsSync(payload);
            })
        );
    }
}
