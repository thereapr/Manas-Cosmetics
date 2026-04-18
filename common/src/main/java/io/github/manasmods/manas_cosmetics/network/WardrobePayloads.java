package io.github.manasmods.manas_cosmetics.network;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S payload types for wardrobe operations.
 * Each record is a typed packet payload sent from the client to the server.
 */
public final class WardrobePayloads {

    private WardrobePayloads() {}

    // ── Equip ──────────────────────────────────────────────────────────────────

    public record EquipPayload(String slotId, String cosmeticId, boolean force)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<EquipPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "equip_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, EquipPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.slotId()); buf.writeUtf(p.cosmeticId()); buf.writeBoolean(p.force()); },
                        buf -> new EquipPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Unequip ────────────────────────────────────────────────────────────────

    public record UnequipPayload(String slotId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<UnequipPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "unequip_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UnequipPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.slotId()),
                        buf -> new UnequipPayload(buf.readUtf())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Force-equip toggle ─────────────────────────────────────────────────────

    public record ForceEquipPayload(String slotId, boolean value) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ForceEquipPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "force_equip_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ForceEquipPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.slotId()); buf.writeBoolean(p.value()); },
                        buf -> new ForceEquipPayload(buf.readUtf(), buf.readBoolean())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Preset save ────────────────────────────────────────────────────────────

    public record PresetSavePayload(String name) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PresetSavePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "preset_save_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PresetSavePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.name()),
                        buf -> new PresetSavePayload(buf.readUtf())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Preset load ────────────────────────────────────────────────────────────

    public record PresetLoadPayload(int index) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PresetLoadPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "preset_load_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PresetLoadPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeVarInt(p.index()),
                        buf -> new PresetLoadPayload(buf.readVarInt())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Preset delete ──────────────────────────────────────────────────────────

    public record PresetDeletePayload(int index) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<PresetDeletePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "preset_delete_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PresetDeletePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeVarInt(p.index()),
                        buf -> new PresetDeletePayload(buf.readVarInt())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Per-weapon-type equip ──────────────────────────────────────────────────

    public record EquipWeaponPayload(String weaponTypeId, String cosmeticId, boolean force)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<EquipWeaponPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "equip_weapon_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, EquipWeaponPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.weaponTypeId()); buf.writeUtf(p.cosmeticId()); buf.writeBoolean(p.force()); },
                        buf -> new EquipWeaponPayload(buf.readUtf(), buf.readUtf(), buf.readBoolean())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UnequipWeaponPayload(String weaponTypeId) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<UnequipWeaponPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "unequip_weapon_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, UnequipWeaponPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeUtf(p.weaponTypeId()),
                        buf -> new UnequipWeaponPayload(buf.readUtf())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ForceEquipWeaponPayload(String weaponTypeId, boolean value) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ForceEquipWeaponPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "force_equip_weapon_c2s"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ForceEquipWeaponPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> { buf.writeUtf(p.weaponTypeId()); buf.writeBoolean(p.value()); },
                        buf -> new ForceEquipWeaponPayload(buf.readUtf(), buf.readBoolean())
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Open wardrobe (C2S) ────────────────────────────────────────────────────

    public record OpenWardrobePayload() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<OpenWardrobePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "open_wardrobe"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobePayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> {},
                        buf -> new OpenWardrobePayload()
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ── Open wardrobe (S2C) ────────────────────────────────────────────────────

    public record OpenWardrobeS2CPayload() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<OpenWardrobeS2CPayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "open_wardrobe_s2c"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobeS2CPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> {},
                        buf -> new OpenWardrobeS2CPayload()
                );

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
