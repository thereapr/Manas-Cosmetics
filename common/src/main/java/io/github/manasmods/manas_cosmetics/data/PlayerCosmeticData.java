package io.github.manasmods.manas_cosmetics.data;

import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Per-player cosmetic state:
 *  - Which cosmetic ID is equipped in each slot
 *  - Which weapon slots have Force Equip enabled
 *  - Up to {@value CosmeticPreset#MAX_PRESETS} named presets
 *
 * Persisted to {@code config/manas_cosmetics/playerdata/<uuid>.dat} using
 * compressed NBT (written on quit, loaded on join).
 */
public final class PlayerCosmeticData {

    private static final Logger LOGGER = LoggerFactory.getLogger("manas_cosmetics");
    private static final String NBT_KEY = "manas_cosmetics:data";

    private final Map<CosmeticSlot, String> equipped = new EnumMap<>(CosmeticSlot.class);
    private final Map<CosmeticSlot, Boolean> forceEquip = new EnumMap<>(CosmeticSlot.class);
    private final List<CosmeticPreset> presets = new ArrayList<>();

    // ── Equip / Unequip ────────────────────────────────────────────────────────

    public void equip(CosmeticSlot slot, String cosmeticId) {
        equipped.put(slot, cosmeticId);
    }

    public void unequip(CosmeticSlot slot) {
        equipped.remove(slot);
        forceEquip.remove(slot);
    }

    public Optional<String> getEquipped(CosmeticSlot slot) {
        return Optional.ofNullable(equipped.get(slot));
    }

    public Map<CosmeticSlot, String> getAllEquipped() {
        return Collections.unmodifiableMap(equipped);
    }

    public boolean isEquipped(CosmeticSlot slot) {
        return equipped.containsKey(slot);
    }

    // ── Force Equip ────────────────────────────────────────────────────────────

    public void setForceEquip(CosmeticSlot slot, boolean value) {
        if (value) {
            forceEquip.put(slot, true);
        } else {
            forceEquip.remove(slot);
        }
    }

    public boolean isForceEquip(CosmeticSlot slot) {
        return forceEquip.getOrDefault(slot, false);
    }

    // ── Presets ────────────────────────────────────────────────────────────────

    public List<CosmeticPreset> getPresets() {
        return Collections.unmodifiableList(presets);
    }

    /**
     * Saves the current loadout as a new named preset.
     * @return false if the preset cap ({@value CosmeticPreset#MAX_PRESETS}) has been reached.
     */
    public boolean savePreset(String name) {
        if (presets.size() >= CosmeticPreset.MAX_PRESETS) return false;
        CosmeticPreset preset = new CosmeticPreset(name);
        preset.getEquippedIds().putAll(equipped);
        preset.getForceEquip().putAll(forceEquip);
        presets.add(preset);
        return true;
    }

    /** Overwrites an existing preset at the given index. */
    public boolean overwritePreset(int index, String name) {
        if (index < 0 || index >= presets.size()) return false;
        CosmeticPreset preset = new CosmeticPreset(name);
        preset.getEquippedIds().putAll(equipped);
        preset.getForceEquip().putAll(forceEquip);
        presets.set(index, preset);
        return true;
    }

    /** Loads a preset by index, replacing the current equipped state. */
    public boolean loadPreset(int index) {
        if (index < 0 || index >= presets.size()) return false;
        CosmeticPreset preset = presets.get(index);
        equipped.clear();
        forceEquip.clear();
        equipped.putAll(preset.getEquippedIds());
        forceEquip.putAll(preset.getForceEquip());
        return true;
    }

    public boolean deletePreset(int index) {
        if (index < 0 || index >= presets.size()) return false;
        presets.remove(index);
        return true;
    }

    // ── NBT serialisation ──────────────────────────────────────────────────────

    public void save(CompoundTag playerTag) {
        CompoundTag data = new CompoundTag();

        CompoundTag equippedTag = new CompoundTag();
        equipped.forEach((slot, id) -> equippedTag.putString(slot.getId(), id));
        data.put("equipped", equippedTag);

        CompoundTag forceTag = new CompoundTag();
        forceEquip.forEach((slot, v) -> forceTag.putBoolean(slot.getId(), v));
        data.put("force_equip", forceTag);

        ListTag presetsTag = new ListTag();
        presets.stream().map(CosmeticPreset::save).forEach(presetsTag::add);
        data.put("presets", presetsTag);

        playerTag.put(NBT_KEY, data);
    }

    public void load(CompoundTag playerTag) {
        if (!playerTag.contains(NBT_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag data = playerTag.getCompound(NBT_KEY);

        equipped.clear();
        if (data.contains("equipped", Tag.TAG_COMPOUND)) {
            CompoundTag equippedTag = data.getCompound("equipped");
            for (String key : equippedTag.getAllKeys()) {
                CosmeticSlot.fromId(key).ifPresent(slot ->
                    equipped.put(slot, equippedTag.getString(key))
                );
            }
        }

        forceEquip.clear();
        if (data.contains("force_equip", Tag.TAG_COMPOUND)) {
            CompoundTag forceTag = data.getCompound("force_equip");
            for (String key : forceTag.getAllKeys()) {
                CosmeticSlot.fromId(key).ifPresent(slot ->
                    forceEquip.put(slot, forceTag.getBoolean(key))
                );
            }
        }

        presets.clear();
        if (data.contains("presets", Tag.TAG_LIST)) {
            ListTag presetsTag = data.getList("presets", Tag.TAG_COMPOUND);
            for (int i = 0; i < presetsTag.size(); i++) {
                presets.add(CosmeticPreset.load(presetsTag.getCompound(i)));
            }
        }
    }

    // ── Static helpers ─────────────────────────────────────────────────────────

    private static final Map<UUID, PlayerCosmeticData> SERVER_CACHE = new HashMap<>();

    public static PlayerCosmeticData of(ServerPlayer player) {
        return SERVER_CACHE.computeIfAbsent(player.getUUID(), k -> new PlayerCosmeticData());
    }

    // ── File-based persistence ─────────────────────────────────────────────────

    /**
     * Loads the player's cosmetic data from
     * {@code config/manas_cosmetics/playerdata/<uuid>.dat} into the server cache.
     * Called on PLAYER_JOIN before syncing state to clients.
     */
    public static void loadFromFile(MinecraftServer server, UUID uuid) {
        Path file = dataFile(server, uuid);
        PlayerCosmeticData data = SERVER_CACHE.computeIfAbsent(uuid, k -> new PlayerCosmeticData());
        if (!Files.exists(file)) return;
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            data.load(tag);
        } catch (IOException e) {
            LOGGER.error("[manas_cosmetics] Failed to load player data for {}", uuid, e);
        }
    }

    /**
     * Saves the player's current cosmetic data to
     * {@code config/manas_cosmetics/playerdata/<uuid>.dat}.
     * Called on PLAYER_QUIT before the cache entry is removed.
     */
    public static void saveToFile(MinecraftServer server, UUID uuid) {
        PlayerCosmeticData data = SERVER_CACHE.get(uuid);
        if (data == null) return;
        Path file = dataFile(server, uuid);
        try {
            Files.createDirectories(file.getParent());
            CompoundTag tag = new CompoundTag();
            data.save(tag);
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            LOGGER.error("[manas_cosmetics] Failed to save player data for {}", uuid, e);
        }
    }

    public static void onPlayerQuit(MinecraftServer server, ServerPlayer player) {
        saveToFile(server, player.getUUID());
        SERVER_CACHE.remove(player.getUUID());
    }

    private static Path dataFile(MinecraftServer server, UUID uuid) {
        return server.getServerDirectory()
            .resolve("config")
            .resolve("manas_cosmetics")
            .resolve("playerdata")
            .resolve(uuid + ".dat");
    }
}
