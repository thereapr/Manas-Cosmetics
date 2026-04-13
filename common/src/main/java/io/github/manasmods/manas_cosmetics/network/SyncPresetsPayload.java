package io.github.manasmods.manas_cosmetics.network;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.data.CosmeticPreset;
import io.github.manasmods.manas_cosmetics.data.PlayerCosmeticData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C packet that sends the owning player's saved presets back to their client on login.
 * Never broadcast to other players — each player only receives their own presets.
 */
public final class SyncPresetsPayload implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "sync_presets");

    public static final CustomPacketPayload.Type<SyncPresetsPayload> TYPE =
        new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPresetsPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> payload.encode(buf),
            SyncPresetsPayload::decode
        );

    /**
     * Wire-format representation of a single preset (slot IDs rather than enum refs so the
     * format stays stable if slots are added or renamed).
     */
    public record WirePreset(
        String name,
        Map<String, String> equipped,    // slot_id → cosmetic_id
        Map<String, Boolean> forceEquip  // slot_id → flag
    ) {}

    private final List<WirePreset> presets;

    public SyncPresetsPayload(List<WirePreset> presets) {
        this.presets = presets;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public List<WirePreset> getPresets() { return presets; }

    // ── Factory ────────────────────────────────────────────────────────────────

    public static SyncPresetsPayload of(ServerPlayer player) {
        List<CosmeticPreset> serverPresets = PlayerCosmeticData.of(player).getPresets();
        List<WirePreset> wire = new ArrayList<>(serverPresets.size());
        for (CosmeticPreset p : serverPresets) {
            Map<String, String> eq = new HashMap<>();
            p.getEquippedIds().forEach((slot, id) -> eq.put(slot.getId(), id));
            Map<String, Boolean> fe = new HashMap<>();
            p.getForceEquip().forEach((slot, v) -> fe.put(slot.getId(), v));
            wire.add(new WirePreset(p.getName(), eq, fe));
        }
        return new SyncPresetsPayload(wire);
    }

    // ── Encode / Decode ────────────────────────────────────────────────────────

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(presets.size());
        for (WirePreset p : presets) {
            buf.writeUtf(p.name());

            buf.writeVarInt(p.equipped().size());
            p.equipped().forEach((slot, id) -> {
                buf.writeUtf(slot);
                buf.writeUtf(id);
            });

            buf.writeVarInt(p.forceEquip().size());
            p.forceEquip().forEach((slot, v) -> {
                buf.writeUtf(slot);
                buf.writeBoolean(v);
            });
        }
    }

    public static SyncPresetsPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<WirePreset> presets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf();

            int eqSize = buf.readVarInt();
            Map<String, String> eq = new HashMap<>(eqSize);
            for (int j = 0; j < eqSize; j++) eq.put(buf.readUtf(), buf.readUtf());

            int feSize = buf.readVarInt();
            Map<String, Boolean> fe = new HashMap<>(feSize);
            for (int j = 0; j < feSize; j++) fe.put(buf.readUtf(), buf.readBoolean());

            presets.add(new WirePreset(name, eq, fe));
        }
        return new SyncPresetsPayload(presets);
    }
}
