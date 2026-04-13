package io.github.manasmods.manas_cosmetics.network;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers all network packets and provides server-side send helpers.
 *
 * Packet IDs:
 *  S2C  manas_cosmetics:sync_player_cosmetics – sync equipped state to clients
 *  C2S  manas_cosmetics:open_wardrobe         – client requests wardrobe open
 */
public final class CosmeticsNetworking {

    private CosmeticsNetworking() {}

    public static void registerCommon() {
        // C2S: client signals it wants to open the wardrobe (server validates & responds)
        NetworkManager.registerReceiver(
            WardrobePayloads.OpenWardrobePayload.TYPE,
            WardrobePayloads.OpenWardrobePayload.STREAM_CODEC,
            (payload, context) -> {
                // No payload needed – just trigger the server-side wardrobe open logic.
                // The actual GUI opens on the client side after login sync.
            }
        );
    }

    /** Broadcasts a player's cosmetics to all players tracking them. */
    public static void syncToTrackers(ServerPlayer player) {
        SyncPlayerCosmeticsPayload payload = SyncPlayerCosmeticsPayload.of(player);

        // Send to the player themselves
        sendSyncToPlayer(payload, player);

        // Send to all other players who can see this player
        player.serverLevel().getChunkSource().broadcastAndSend(
            player,
            NetworkManager.toPacket(NetworkManager.Side.S2C, payload)
        );
    }

    /** Sends cosmetic sync only to the given player (e.g. on login). */
    public static void sendSyncToPlayer(SyncPlayerCosmeticsPayload payload, ServerPlayer recipient) {
        NetworkManager.sendToPlayer(recipient, payload);
    }
}
