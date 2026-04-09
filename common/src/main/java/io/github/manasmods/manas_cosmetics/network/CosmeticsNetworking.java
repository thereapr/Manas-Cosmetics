package io.github.manasmods.manas_cosmetics.network;

import dev.architectury.networking.NetworkManager;
import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Registers all network packets and provides server-side send helpers.
 *
 * Packet IDs:
 *  S2C  manas_cosmetics:sync_player_cosmetics – sync equipped state to clients
 *  C2S  manas_cosmetics:open_wardrobe         – client requests wardrobe open
 */
public final class CosmeticsNetworking {

    public static final ResourceLocation OPEN_WARDROBE_C2S =
        ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "open_wardrobe");

    private CosmeticsNetworking() {}

    public static void registerCommon() {
        // C2S: client signals it wants to open the wardrobe (server validates & responds)
        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            OPEN_WARDROBE_C2S,
            (buf, context) -> {
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
            buildSyncPacket(payload)
        );
    }

    /** Sends cosmetic sync only to the given player (e.g. on login). */
    public static void sendSyncToPlayer(SyncPlayerCosmeticsPayload payload, ServerPlayer recipient) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        payload.encode(buf);
        NetworkManager.sendToPlayer(recipient, SyncPlayerCosmeticsPayload.ID, buf);
    }

    private static net.minecraft.network.protocol.Packet<?> buildSyncPacket(SyncPlayerCosmeticsPayload payload) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        payload.encode(buf);
        return NetworkManager.toPacket(NetworkManager.Side.S2C, SyncPlayerCosmeticsPayload.ID, buf);
    }
}
