package io.github.manasmods.manas_cosmetics.data;

import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.Map;

/** A named snapshot of the player's full cosmetic loadout. */
public final class CosmeticPreset {

    public static final int MAX_PRESETS = 10;

    private String name;
    private final Map<CosmeticSlot, String> equippedIds = new EnumMap<>(CosmeticSlot.class);
    private final Map<CosmeticSlot, Boolean> forceEquip = new EnumMap<>(CosmeticSlot.class);

    public CosmeticPreset(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<CosmeticSlot, String> getEquippedIds() { return equippedIds; }
    public Map<CosmeticSlot, Boolean> getForceEquip() { return forceEquip; }

    // ── NBT serialisation ──────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);

        CompoundTag equipped = new CompoundTag();
        equippedIds.forEach((slot, id) -> equipped.putString(slot.getId(), id));
        tag.put("equipped", equipped);

        CompoundTag force = new CompoundTag();
        forceEquip.forEach((slot, v) -> force.putBoolean(slot.getId(), v));
        tag.put("force_equip", force);

        return tag;
    }

    public static CosmeticPreset load(CompoundTag tag) {
        CosmeticPreset preset = new CosmeticPreset(tag.getString("name"));

        if (tag.contains("equipped", Tag.TAG_COMPOUND)) {
            CompoundTag equipped = tag.getCompound("equipped");
            for (String key : equipped.getAllKeys()) {
                CosmeticSlot.fromId(key).ifPresent(slot ->
                    preset.equippedIds.put(slot, equipped.getString(key))
                );
            }
        }

        if (tag.contains("force_equip", Tag.TAG_COMPOUND)) {
            CompoundTag force = tag.getCompound("force_equip");
            for (String key : force.getAllKeys()) {
                CosmeticSlot.fromId(key).ifPresent(slot ->
                    preset.forceEquip.put(slot, force.getBoolean(key))
                );
            }
        }

        return preset;
    }
}
