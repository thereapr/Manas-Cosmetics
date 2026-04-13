package io.github.manasmods.manas_cosmetics.client;

import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
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

    // Local player equipped state (mirrors server)
    private final Map<CosmeticSlot, String> equipped = new EnumMap<>(CosmeticSlot.class);
    private final Map<CosmeticSlot, Boolean> forceEquip = new EnumMap<>(CosmeticSlot.class);
    private final List<CosmeticPreset> presets = new ArrayList<>();

    // All other players' states (UUID → slot → cosmeticId), for rendering
    private final Map<UUID, Map<CosmeticSlot, String>> otherPlayers = new HashMap<>();
    private final Map<UUID, Map<CosmeticSlot, Boolean>> otherPlayersForce = new HashMap<>();

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

        if (payload.getTargetPlayer().equals(localPlayerUUID)) {
            equipped.clear();
            equipped.putAll(equippedMap);
            forceEquip.clear();
            forceEquip.putAll(forceMap);
        } else {
            otherPlayers.put(payload.getTargetPlayer(), equippedMap);
            otherPlayersForce.put(payload.getTargetPlayer(), forceMap);
        }
    }

    // ── Local player queries ───────────────────────────────────────────────────

    public boolean isEquipped(CosmeticSlot slot, String id) {
        return id.equals(equipped.get(slot));
    }

    public Optional<String> getEquipped(CosmeticSlot slot) {
        return Optional.ofNullable(equipped.get(slot));
    }

    public boolean isForceEquip(CosmeticSlot slot) {
        return forceEquip.getOrDefault(slot, false);
    }

    public List<CosmeticPreset> getPresets() { return Collections.unmodifiableList(presets); }

    // Optimistic local mutations (also sent to server as C2S packets from WardrobeScreen)
    public void equip(CosmeticSlot slot, String id) { equipped.put(slot, id); }
    public void unequip(CosmeticSlot slot) { equipped.remove(slot); forceEquip.remove(slot); }
    public void setForceEquip(CosmeticSlot slot, boolean v) {
        if (v) forceEquip.put(slot, true); else forceEquip.remove(slot);
    }

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

    /**
     * Replaces the local preset list with the authoritative copy received from the server
     * on login. Called from the {@code SyncPresetsPayload} S2C receiver.
     */
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

    public void removePlayer(UUID uuid) {
        otherPlayers.remove(uuid);
        otherPlayersForce.remove(uuid);
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
        presets.clear();
        otherPlayers.clear();
        otherPlayersForce.clear();
    }
}
