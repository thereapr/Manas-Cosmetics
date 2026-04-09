package io.github.manasmods.manas_cosmetics.core;

import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.data.PlayerCosmeticData;
import io.github.manasmods.manas_cosmetics.entity.EntityRegistry;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side lifecycle manager for {@link PetCosmeticEntity}.
 *
 * One pet entity per player — spawned when the player has a pet cosmetic equipped
 * (on login or on equip), despawned when they unequip or quit.
 */
public final class PetManager {

    private static final PetManager INSTANCE = new PetManager();

    /** Maps player UUID → the UUID of their active pet entity. */
    private final Map<UUID, UUID> activePets = new HashMap<>();

    private PetManager() {}

    public static PetManager get() { return INSTANCE; }

    // ── Spawn ──────────────────────────────────────────────────────────────────

    /**
     * Spawns (or re-spawns) the pet for {@code player} if they have a pet cosmetic equipped.
     * Safe to call on login or after a reload.
     */
    public void spawnIfEquipped(ServerPlayer player) {
        PlayerCosmeticData data = PlayerCosmeticData.of(player);
        data.getEquipped(CosmeticSlot.PET).ifPresent(cosmeticId -> spawn(player, cosmeticId));
    }

    public void spawn(ServerPlayer player, String cosmeticId) {
        despawn(player); // Remove existing pet first

        ServerLevel level = player.serverLevel();
        PetCosmeticEntity pet = EntityRegistry.PET_COSMETIC.get().create(level);
        if (pet == null) return;

        pet.setOwner(player);
        pet.setCosmeticId(cosmeticId);
        pet.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);

        level.addFreshEntity(pet);
        activePets.put(player.getUUID(), pet.getUUID());
    }

    // ── Despawn ────────────────────────────────────────────────────────────────

    public void despawn(ServerPlayer player) {
        UUID petUUID = activePets.remove(player.getUUID());
        if (petUUID == null) return;

        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(petUUID);
        if (entity instanceof PetCosmeticEntity pet) {
            pet.discard();
        }
    }

    // ── Player lifecycle hooks ─────────────────────────────────────────────────

    public void onPlayerLogin(ServerPlayer player) {
        spawnIfEquipped(player);
    }

    public void onPlayerQuit(ServerPlayer player) {
        despawn(player);
    }

    public void onPlayerRespawn(ServerPlayer player) {
        // Re-spawn after death
        spawnIfEquipped(player);
    }
}
