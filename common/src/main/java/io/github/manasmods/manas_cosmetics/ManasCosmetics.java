package io.github.manasmods.manas_cosmetics;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.command.ManasCosmteticsCommand;
import io.github.manasmods.manas_cosmetics.core.CosmeticManager;
import io.github.manasmods.manas_cosmetics.core.PetManager;
import io.github.manasmods.manas_cosmetics.data.CosmeticPreset;
import io.github.manasmods.manas_cosmetics.data.PlayerCosmeticData;
import io.github.manasmods.manas_cosmetics.entity.EntityRegistry;
import io.github.manasmods.manas_cosmetics.network.CosmeticsNetworking;
import io.github.manasmods.manas_cosmetics.network.SyncCosmeticRegistryPayload;
import io.github.manasmods.manas_cosmetics.network.SyncPlayerCosmeticsPayload;
import io.github.manasmods.manas_cosmetics.network.SyncPresetsPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManasCosmetics {

    public static final String MOD_ID = "manas_cosmetics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        // Entity types
        EntityRegistry.register();

        // Networking (C2S receivers)
        CosmeticsNetworking.registerCommon();
        registerWardrobePackets();

        // Commands
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
            ManasCosmteticsCommand.register(dispatcher)
        );

        // Server lifecycle — init CosmeticManager when server starts
        LifecycleEvent.SERVER_STARTED.register(server -> CosmeticManager.get().init(server));

        // Player data cleanup & file-based save on quit
        // (PlayerEvent.SAVE_DATA / LOAD_DATA are absent in Architectury 13.x;
        //  we use our own file-based persistence in playerdata/<uuid>.dat instead)
        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (player instanceof ServerPlayer sp) {
                PetManager.get().onPlayerQuit(sp);
                PlayerCosmeticData.onPlayerQuit(sp.getServer(), sp);
            }
        });

        // On login:
        //  1. Send this player's cosmetics to all online players (tracking sync)
        //  2. Send ALL online players' cosmetics to this player
        //  3. Send the full cosmetic registry to this player
        //  4. Spawn the player's pet if they have one equipped
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (!(player instanceof ServerPlayer sp)) return;

            // Load player's saved cosmetic data from disk before syncing
            PlayerCosmeticData.loadFromFile(sp.getServer(), sp.getUUID());

            // Sync this player's state to everyone currently tracking them
            CosmeticsNetworking.syncToTrackers(sp);

            // Sync every other online player's state to the joiner
            sp.getServer().getPlayerList().getPlayers().forEach(other -> {
                if (!other.getUUID().equals(sp.getUUID())) {
                    CosmeticsNetworking.sendSyncToPlayer(SyncPlayerCosmeticsPayload.of(other), sp);
                }
            });

            // Send cosmetic registry (definitions + models) to the client
            sendRegistryToPlayer(sp);

            // Send the player's saved presets to their client
            sendPresetsToPlayer(sp);

            // Spawn pet entity if one is equipped
            PetManager.get().onPlayerLogin(sp);
        });

        // After respawn, re-spawn the pet
        PlayerEvent.PLAYER_RESPAWN.register((newPlayer, oldLevel, conqueredEnd) -> {
            if (newPlayer instanceof ServerPlayer sp) PetManager.get().onPlayerRespawn(sp);
        });

        LOGGER.info("[manas_cosmetics] Common init complete.");
    }

    // ── Registry sync ──────────────────────────────────────────────────────────

    public static void sendRegistryToPlayer(ServerPlayer player) {
        SyncCosmeticRegistryPayload payload = SyncCosmeticRegistryPayload.fromManager();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        payload.encode(buf);
        NetworkManager.sendToPlayer(player, SyncCosmeticRegistryPayload.ID, buf);
    }

    public static void sendPresetsToPlayer(ServerPlayer player) {
        SyncPresetsPayload payload = SyncPresetsPayload.of(player);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), player.registryAccess());
        payload.encode(buf);
        NetworkManager.sendToPlayer(player, SyncPresetsPayload.ID, buf);
    }

    /** Called after a server-side reload so all clients update their registry. */
    public static void broadcastRegistryToAll(net.minecraft.server.MinecraftServer server) {
        SyncCosmeticRegistryPayload payload = SyncCosmeticRegistryPayload.fromManager();
        server.getPlayerList().getPlayers().forEach(sp -> {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), sp.registryAccess());
            payload.encode(buf);
            NetworkManager.sendToPlayer(sp, SyncCosmeticRegistryPayload.ID, buf);
        });
    }

    // ── Wardrobe C2S packets ───────────────────────────────────────────────────

    private static void registerWardrobePackets() {
        final NetworkManager.Side C2S = NetworkManager.Side.C2S;

        // Equip
        NetworkManager.registerReceiver(C2S, rl("equip_c2s"), (buf, ctx) -> {
            String slotId = buf.readUtf();
            String id     = buf.readUtf();
            boolean force = buf.readBoolean();
            ctx.queue(() -> {
                ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                CosmeticSlot.fromId(slotId).ifPresent(slot -> {
                    if (!CosmeticManager.get().exists(id)) return;
                    PlayerCosmeticData data = PlayerCosmeticData.of(sp);
                    data.equip(slot, id);
                    data.setForceEquip(slot, force);
                    // Spawn / despawn pet as needed
                    if (slot == CosmeticSlot.PET) PetManager.get().spawn(sp, id);
                    CosmeticsNetworking.syncToTrackers(sp);
                });
            });
        });

        // Unequip
        NetworkManager.registerReceiver(C2S, rl("unequip_c2s"), (buf, ctx) -> {
            String slotId = buf.readUtf();
            ctx.queue(() -> {
                ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                CosmeticSlot.fromId(slotId).ifPresent(slot -> {
                    PlayerCosmeticData.of(sp).unequip(slot);
                    if (slot == CosmeticSlot.PET) PetManager.get().despawn(sp);
                    CosmeticsNetworking.syncToTrackers(sp);
                });
            });
        });

        // Force equip toggle
        NetworkManager.registerReceiver(C2S, rl("force_equip_c2s"), (buf, ctx) -> {
            String slotId = buf.readUtf();
            boolean value = buf.readBoolean();
            ctx.queue(() -> {
                ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                CosmeticSlot.fromId(slotId).ifPresent(slot -> {
                    PlayerCosmeticData.of(sp).setForceEquip(slot, value);
                    CosmeticsNetworking.syncToTrackers(sp);
                });
            });
        });

        // Preset save
        NetworkManager.registerReceiver(C2S, rl("preset_save_c2s"), (buf, ctx) -> {
            String name = buf.readUtf();
            ctx.queue(() -> {
                ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                if (!PlayerCosmeticData.of(sp).savePreset(name)) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "[manas_cosmetics] Preset limit reached (max " + CosmeticPreset.MAX_PRESETS + ")."));
                }
            });
        });

        // Preset load
        NetworkManager.registerReceiver(C2S, rl("preset_load_c2s"), (buf, ctx) -> {
            int index = buf.readVarInt();
            ctx.queue(() -> {
                ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                PlayerCosmeticData.of(sp).loadPreset(index);
                // Re-evaluate pet slot after preset switch
                PlayerCosmeticData.of(sp).getEquipped(CosmeticSlot.PET)
                    .ifPresentOrElse(
                        id -> PetManager.get().spawn(sp, id),
                        () -> PetManager.get().despawn(sp)
                    );
                CosmeticsNetworking.syncToTrackers(sp);
            });
        });

        // Preset delete
        NetworkManager.registerReceiver(C2S, rl("preset_delete_c2s"), (buf, ctx) -> {
            int index = buf.readVarInt();
            ctx.queue(() -> PlayerCosmeticData.of((ServerPlayer) ctx.getPlayer()).deletePreset(index));
        });
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
