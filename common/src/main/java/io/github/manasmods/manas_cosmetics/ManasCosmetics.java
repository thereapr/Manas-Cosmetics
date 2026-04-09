package io.github.manasmods.manas_cosmetics;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.PlayerEvent;
import io.github.manasmods.manas_cosmetics.command.ManasCosmteticsCommand;
import io.github.manasmods.manas_cosmetics.core.CosmeticManager;
import io.github.manasmods.manas_cosmetics.data.PlayerCosmeticData;
import io.github.manasmods.manas_cosmetics.network.CosmeticsNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManasCosmetics {

    public static final String MOD_ID = "manas_cosmetics";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        // Register networking (C2S receivers)
        CosmeticsNetworking.registerCommon();
        registerWardrobePackets();

        // Register commands
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) ->
            ManasCosmteticsCommand.register(dispatcher)
        );

        // Player data: save / load / cleanup
        PlayerEvent.SAVE_DATA.register((player, tag) -> {
            if (player instanceof ServerPlayer sp) {
                PlayerCosmeticData.onPlayerSave(sp, tag);
            }
        });

        PlayerEvent.LOAD_DATA.register((player, tag) -> {
            if (player instanceof ServerPlayer sp) {
                PlayerCosmeticData.onPlayerLoad(sp, tag);
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (player instanceof ServerPlayer sp) {
                PlayerCosmeticData.onPlayerQuit(sp);
            }
        });

        // Sync cosmetics to client on login
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (player instanceof ServerPlayer sp) {
                CosmeticsNetworking.sendSyncToPlayer(
                    io.github.manasmods.manas_cosmetics.network.SyncPlayerCosmeticsPayload.of(sp),
                    sp
                );
            }
        });

        // Initialise the CosmeticManager when the server starts
        dev.architectury.event.events.common.LifecycleEvent.SERVER_STARTED.register(server ->
            CosmeticManager.get().init(server)
        );

        LOGGER.info("[manas_cosmetics] Common init complete.");
    }

    /** Register C2S packets that mutate player cosmetic state on the server. */
    private static void registerWardrobePackets() {
        var nm = dev.architectury.networking.NetworkManager.Side.C2S;

        // Equip
        dev.architectury.networking.NetworkManager.registerReceiver(nm,
            new net.minecraft.resources.ResourceLocation(MOD_ID, "equip_c2s"),
            (buf, ctx) -> {
                String slotId   = buf.readUtf();
                String id       = buf.readUtf();
                boolean force   = buf.readBoolean();
                ctx.queue(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                    io.github.manasmods.manas_cosmetics.api.CosmeticSlot.fromId(slotId).ifPresent(slot -> {
                        if (!CosmeticManager.get().exists(id)) return;
                        PlayerCosmeticData data = PlayerCosmeticData.of(sp);
                        data.equip(slot, id);
                        data.setForceEquip(slot, force);
                        CosmeticsNetworking.syncToTrackers(sp);
                    });
                });
            });

        // Unequip
        dev.architectury.networking.NetworkManager.registerReceiver(nm,
            new net.minecraft.resources.ResourceLocation(MOD_ID, "unequip_c2s"),
            (buf, ctx) -> {
                String slotId = buf.readUtf();
                ctx.queue(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                    io.github.manasmods.manas_cosmetics.api.CosmeticSlot.fromId(slotId).ifPresent(slot -> {
                        PlayerCosmeticData.of(sp).unequip(slot);
                        CosmeticsNetworking.syncToTrackers(sp);
                    });
                });
            });

        // Force equip toggle
        dev.architectury.networking.NetworkManager.registerReceiver(nm,
            new net.minecraft.resources.ResourceLocation(MOD_ID, "force_equip_c2s"),
            (buf, ctx) -> {
                String slotId = buf.readUtf();
                boolean value = buf.readBoolean();
                ctx.queue(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                    io.github.manasmods.manas_cosmetics.api.CosmeticSlot.fromId(slotId).ifPresent(slot -> {
                        PlayerCosmeticData.of(sp).setForceEquip(slot, value);
                        CosmeticsNetworking.syncToTrackers(sp);
                    });
                });
            });

        // Preset save
        dev.architectury.networking.NetworkManager.registerReceiver(nm,
            new net.minecraft.resources.ResourceLocation(MOD_ID, "preset_save_c2s"),
            (buf, ctx) -> {
                String name = buf.readUtf();
                ctx.queue(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                    PlayerCosmeticData data = PlayerCosmeticData.of(sp);
                    if (!data.savePreset(name)) {
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[manas_cosmetics] Preset limit reached (max " +
                            io.github.manasmods.manas_cosmetics.data.CosmeticPreset.MAX_PRESETS + ")."
                        ));
                    }
                });
            });

        // Preset load
        dev.architectury.networking.NetworkManager.registerReceiver(nm,
            new net.minecraft.resources.ResourceLocation(MOD_ID, "preset_load_c2s"),
            (buf, ctx) -> {
                int index = buf.readVarInt();
                ctx.queue(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                    PlayerCosmeticData.of(sp).loadPreset(index);
                    CosmeticsNetworking.syncToTrackers(sp);
                });
            });

        // Preset delete
        dev.architectury.networking.NetworkManager.registerReceiver(nm,
            new net.minecraft.resources.ResourceLocation(MOD_ID, "preset_delete_c2s"),
            (buf, ctx) -> {
                int index = buf.readVarInt();
                ctx.queue(() -> {
                    ServerPlayer sp = (ServerPlayer) ctx.getPlayer();
                    PlayerCosmeticData.of(sp).deletePreset(index);
                });
            });
    }
}
