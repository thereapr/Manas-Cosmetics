package io.github.manasmods.manas_cosmetics.network;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.data.PlayerCosmeticData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S2C packet that syncs a player's equipped cosmetics and force-equip flags to all clients.
 * Sent on login and whenever the equipped state changes.
 */
public final class SyncPlayerCosmeticsPayload implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "sync_player_cosmetics");

    public static final CustomPacketPayload.Type<SyncPlayerCosmeticsPayload> TYPE =
        new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerCosmeticsPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> payload.encode(buf),
            SyncPlayerCosmeticsPayload::decode
        );

    private final UUID targetPlayer;
    private final Map<String, String> equipped;         // slot id → cosmetic id
    private final Map<String, Boolean> forceEquip;       // slot id → flag
    private final Map<String, String> equippedWeapon;    // weaponType id → cosmetic id
    private final Map<String, Boolean> forceEquipWeapon; // weaponType id → flag

    public SyncPlayerCosmeticsPayload(UUID targetPlayer,
                                      Map<String, String> equipped,
                                      Map<String, Boolean> forceEquip,
                                      Map<String, String> equippedWeapon,
                                      Map<String, Boolean> forceEquipWeapon) {
        this.targetPlayer = targetPlayer;
        this.equipped = equipped;
        this.forceEquip = forceEquip;
        this.equippedWeapon = equippedWeapon;
        this.forceEquipWeapon = forceEquipWeapon;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public UUID getTargetPlayer() { return targetPlayer; }
    public Map<String, String> getEquipped() { return equipped; }
    public Map<String, Boolean> getForceEquip() { return forceEquip; }
    public Map<String, String> getEquippedWeapon() { return equippedWeapon; }
    public Map<String, Boolean> getForceEquipWeapon() { return forceEquipWeapon; }

    // ── Encode / Decode ────────────────────────────────────────────────────────

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(targetPlayer);

        buf.writeVarInt(equipped.size());
        equipped.forEach((slot, id) -> { buf.writeUtf(slot); buf.writeUtf(id); });

        buf.writeVarInt(forceEquip.size());
        forceEquip.forEach((slot, v) -> { buf.writeUtf(slot); buf.writeBoolean(v); });

        buf.writeVarInt(equippedWeapon.size());
        equippedWeapon.forEach((wt, id) -> { buf.writeUtf(wt); buf.writeUtf(id); });

        buf.writeVarInt(forceEquipWeapon.size());
        forceEquipWeapon.forEach((wt, v) -> { buf.writeUtf(wt); buf.writeBoolean(v); });
    }

    public static SyncPlayerCosmeticsPayload decode(RegistryFriendlyByteBuf buf) {
        UUID uuid = buf.readUUID();

        int equippedSize = buf.readVarInt();
        Map<String, String> equipped = new HashMap<>(equippedSize);
        for (int i = 0; i < equippedSize; i++) equipped.put(buf.readUtf(), buf.readUtf());

        int forceSize = buf.readVarInt();
        Map<String, Boolean> forceEquip = new HashMap<>(forceSize);
        for (int i = 0; i < forceSize; i++) forceEquip.put(buf.readUtf(), buf.readBoolean());

        int weaponSize = buf.readVarInt();
        Map<String, String> equippedWeapon = new HashMap<>(weaponSize);
        for (int i = 0; i < weaponSize; i++) equippedWeapon.put(buf.readUtf(), buf.readUtf());

        int forceWeaponSize = buf.readVarInt();
        Map<String, Boolean> forceEquipWeapon = new HashMap<>(forceWeaponSize);
        for (int i = 0; i < forceWeaponSize; i++) forceEquipWeapon.put(buf.readUtf(), buf.readBoolean());

        return new SyncPlayerCosmeticsPayload(uuid, equipped, forceEquip, equippedWeapon, forceEquipWeapon);
    }

    // ── Convenience factory ────────────────────────────────────────────────────

    public static SyncPlayerCosmeticsPayload of(ServerPlayer player) {
        PlayerCosmeticData data = PlayerCosmeticData.of(player);

        Map<String, String> equipped = new HashMap<>();
        data.getAllEquipped().forEach((slot, id) -> equipped.put(slot.getId(), id));

        Map<String, Boolean> forceEquip = new HashMap<>();
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (data.isForceEquip(slot)) forceEquip.put(slot.getId(), true);
        }

        Map<String, String> equippedWeapon = new HashMap<>();
        data.getAllEquippedWeapon().forEach((wt, id) -> equippedWeapon.put(wt.getId(), id));

        Map<String, Boolean> forceEquipWeapon = new HashMap<>();
        data.getAllForceEquipWeapon().forEach((wt, v) -> forceEquipWeapon.put(wt.getId(), v));

        return new SyncPlayerCosmeticsPayload(player.getUUID(), equipped, forceEquip,
                                               equippedWeapon, forceEquipWeapon);
    }
}
