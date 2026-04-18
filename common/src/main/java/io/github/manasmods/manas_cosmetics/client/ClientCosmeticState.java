package io.github.manasmods.manas_cosmetics.client;

import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.data.CosmeticPreset;
import io.github.manasmods.manas_cosmetics.network.SyncPlayerCosmeticsPayload;
import io.github.manasmods.manas_cosmetics.network.SyncPresetsPayload;

import java.util.*;

/**
 * Client-side mirror of the local player's cosmetic state.
 * Populated from {@link SyncPlayerCosmeticsPayload} on login and after each server-side change.
 *
 * Also holds the client-local cosmetic registry (synced from server on login) so the
 * wardrobe GUI can list available cosmetics without querying the server.
 */
public final class ClientCosmeticState {

    private static final ClientCosmeticState INSTANCE = new ClientCosmeticState();

    // Local player slot-based state
    private final Map<CosmeticSlot, String> equipped = new EnumMap<>(CosmeticSlot.class);
    private final Map<CosmeticSlot, Boolean> forceEquip = new EnumMap<>(CosmeticSlot.class);
    // Local player per-weapon-type state
    private final Map<WeaponType, String> equippedWeapon = new EnumMap<>(WeaponType.class);
    private final Map<WeaponType, Boolean> forceEquipWeapon = new EnumMap<>(WeaponType.class);

    private final List<CosmeticPreset> presets = new ArrayList<>();

    // Other players' states (UUID → slot → cosmeticId), for rendering
    private final Map<UUID, Map<CosmeticSlot, String>> otherPlayers = new HashMap<>();
    private final Map<UUID, Map<CosmeticSlot, Boolean>> otherPlayersForce = new HashMap<>();
    private final Map<UUID, Map<WeaponType, String>> otherPlayersWeapon = new HashMap<>();
    private final Map<UUID, Map<WeaponType, Boolean>> otherPlayersForceWeapon = new HashMap<>();

    // Available cosmetics synced from server
    private final List<CosmeticDefinition> availableCosmetics = new ArrayList<>();

    private ClientCosmeticState() {}

    public static ClientCosmeticState get() { return INSTANCE; }

    // ── Sync from server ───────────────────────────────────────────────────────

    public void handleSync(SyncPlayerCosmeticsPayload payload, UUID localPlayerUUID) {
        Map<CosmeticSlot, String> equippedMap = new EnumMap<>(CosmeticSlot.class);
        payload.getEquipped().forEach((slotId, cosmeticId) ->
            CosmeticSlot.fromId(slotId).ifPresent(slot -> equippedMap.put(slot, cosmeticId))
        );

        Map<CosmeticSlot, Boolean> forceMap = new EnumMap<>(CosmeticSlot.class);
        payload.getForceEquip().forEach((slotId, value) ->
            CosmeticSlot.fromId(slotId).ifPresent(slot -> forceMap.put(slot, value))
        );

        Map<WeaponType, String> weaponMap = new EnumMap<>(WeaponType.class);
        payload.getEquippedWeapon().forEach((wtId, cosmeticId) -> {
            WeaponType wt = WeaponType.fromId(wtId);
            if (wt != WeaponType.ANY) weaponMap.put(wt, cosmeticId);
        });

        Map<WeaponType, Boolean> forceWeaponMap = new EnumMap<>(WeaponType.class);
        payload.getForceEquipWeapon().forEach((wtId, value) -> {
            WeaponType wt = WeaponType.fromId(wtId);
            if (wt != WeaponType.ANY) forceWeaponMap.put(wt, value);
        });

        if (payload.getTargetPlayer().equals(localPlayerUUID)) {
            equipped.clear();
            equipped.putAll(equippedMap);
            forceEquip.clear();
            forceEquip.putAll(forceMap);
            equippedWeapon.clear();
            equippedWeapon.putAll(weaponMap);
            forceEquipWeapon.clear();
            forceEquipWeapon.putAll(forceWeaponMap);
        } else {
            UUID uuid = payload.getTargetPlayer();
            otherPlayers.put(uuid, equippedMap);
            otherPlayersForce.put(uuid, forceMap);
            otherPlayersWeapon.put(uuid, weaponMap);
            otherPlayersForceWeapon.put(uuid, forceWeaponMap);
        }
    }

    // ── Local player slot queries ──────────────────────────────────────────────

    public boolean isEquipped(CosmeticSlot slot, String id) {
        return id.equals(equipped.get(slot));
    }

    public Optional<String> getEquipped(CosmeticSlot slot) {
        return Optional.ofNullable(equipped.get(slot));
    }

    public boolean isForceEquip(CosmeticSlot slot) {
        return forceEquip.getOrDefault(slot, false);
    }

    public void equip(CosmeticSlot slot, String id) { equipped.put(slot, id); }
    public void unequip(CosmeticSlot slot) { equipped.remove(slot); forceEquip.remove(slot); }
    public void setForceEquip(CosmeticSlot slot, boolean v) {
        if (v) forceEquip.put(slot, true); else forceEquip.remove(slot);
    }

    // ── Local player per-weapon-type queries ───────────────────────────────────

    public boolean isEquippedWeapon(WeaponType weaponType, String id) {
        return id.equals(equippedWeapon.get(weaponType));
    }

    public Optional<String> getEquippedWeapon(WeaponType weaponType) {
        return Optional.ofNullable(equippedWeapon.get(weaponType));
    }

    public Map<WeaponType, String> getAllEquippedWeapon() {
        return Collections.unmodifiableMap(equippedWeapon);
    }

    public boolean isForceEquipWeapon(WeaponType weaponType) {
        return forceEquipWeapon.getOrDefault(weaponType, false);
    }

    public void equipWeapon(WeaponType wt, String id) { equippedWeapon.put(wt, id); }
    public void unequipWeapon(WeaponType wt) { equippedWeapon.remove(wt); forceEquipWeapon.remove(wt); }
    public void setForceEquipWeapon(WeaponType wt, boolean v) {
        if (v) forceEquipWeapon.put(wt, true); else forceEquipWeapon.remove(wt);
    }

    // ── Presets ────────────────────────────────────────────────────────────────

    public List<CosmeticPreset> getPresets() { return Collections.unmodifiableList(presets); }

    public boolean savePreset(String name) {
        if (presets.size() >= CosmeticPreset.MAX_PRESETS) return false;
        CosmeticPreset p = new CosmeticPreset(name);
        p.getEquippedIds().putAll(equipped);
        p.getForceEquip().putAll(forceEquip);
        presets.add(p);
        return true;
    }

    public boolean loadPreset(int index) {
        if (index < 0 || index >= presets.size()) return false;
        CosmeticPreset p = presets.get(index);
        equipped.clear(); equipped.putAll(p.getEquippedIds());
        forceEquip.clear(); forceEquip.putAll(p.getForceEquip());
        return true;
    }

    public boolean deletePreset(int index) {
        if (index < 0 || index >= presets.size()) return false;
        presets.remove(index);
        return true;
    }

    // ── Preset sync from server ────────────────────────────────────────────────

    public void handlePresetsSync(SyncPresetsPayload payload) {
        presets.clear();
        for (SyncPresetsPayload.WirePreset wp : payload.getPresets()) {
            CosmeticPreset p = new CosmeticPreset(wp.name());
            wp.equipped().forEach((slotId, cosmeticId) ->
                CosmeticSlot.fromId(slotId).ifPresent(slot -> p.getEquippedIds().put(slot, cosmeticId))
            );
            wp.forceEquip().forEach((slotId, v) ->
                CosmeticSlot.fromId(slotId).ifPresent(slot -> p.getForceEquip().put(slot, v))
            );
            presets.add(p);
        }
    }

    // ── Other players (for rendering) ─────────────────────────────────────────

    public Optional<String> getEquippedForPlayer(UUID uuid, CosmeticSlot slot) {
        Map<CosmeticSlot, String> map = otherPlayers.get(uuid);
        return map == null ? Optional.empty() : Optional.ofNullable(map.get(slot));
    }

    public boolean isForceEquipForPlayer(UUID uuid, CosmeticSlot slot) {
        Map<CosmeticSlot, Boolean> map = otherPlayersForce.get(uuid);
        return map != null && map.getOrDefault(slot, false);
    }

    public Map<WeaponType, String> getAllEquippedWeaponForPlayer(UUID uuid) {
        Map<WeaponType, String> map = otherPlayersWeapon.get(uuid);
        return map == null ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    public boolean isForceEquipWeaponForPlayer(UUID uuid, WeaponType wt) {
        Map<WeaponType, Boolean> map = otherPlayersForceWeapon.get(uuid);
        return map != null && map.getOrDefault(wt, false);
    }

    public void removePlayer(UUID uuid) {
        otherPlayers.remove(uuid);
        otherPlayersForce.remove(uuid);
        otherPlayersWeapon.remove(uuid);
        otherPlayersForceWeapon.remove(uuid);
    }

    // ── Available cosmetics ────────────────────────────────────────────────────

    public List<CosmeticDefinition> getAvailableCosmetics() {
        return Collections.unmodifiableList(availableCosmetics);
    }

    public void setAvailableCosmetics(List<CosmeticDefinition> defs) {
        availableCosmetics.clear();
        availableCosmetics.addAll(defs);
    }

    public void reset() {
        equipped.clear();
        forceEquip.clear();
        equippedWeapon.clear();
        forceEquipWeapon.clear();
        presets.clear();
        otherPlayers.clear();
        otherPlayersForce.clear();
        otherPlayersWeapon.clear();
        otherPlayersForceWeapon.clear();
    }
}
