package io.github.manasmods.manas_cosmetics.entity;

import io.github.manasmods.manas_cosmetics.entity.goal.FollowOwnerGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The cosmetic pet entity that follows its owner player around.
 *
 * Characteristics (per brief):
 *  - Follows owner via pathfinding; teleports when > ~12 blocks away
 *  - Cannot be attacked or targeted by mobs
 *  - Invincible (all damage blocked)
 *  - Not summonable via /summon; not saved to world file (re-spawned from player NBT on login)
 *  - Renders using {@link io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer}
 *    (the entity has no visual of its own; the layer draws the BBModel on top)
 */
public class PetCosmeticEntity extends PathfinderMob {

    private static final String NBT_OWNER      = "OwnerUUID";
    private static final String NBT_COSMETIC   = "CosmeticId";

    @Nullable private UUID ownerUUID;
    private String cosmeticId = "";

    public PetCosmeticEntity(EntityType<? extends PetCosmeticEntity> type, Level level) {
        super(type, level);
        setNoAi(false);
    }

    // ── Attribute defaults ─────────────────────────────────────────────────────

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // ── Goals ──────────────────────────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FollowOwnerGoal(this, 1.0));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8f));
    }

    // ── Non-interactive / invincible ───────────────────────────────────────────

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // Invincible — no damage taken
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false; // Mobs do not target this entity
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ── Owner ──────────────────────────────────────────────────────────────────

    public void setOwner(Player player) {
        this.ownerUUID = player.getUUID();
    }

    @Nullable
    public LivingEntity getOwnerEntity() {
        if (ownerUUID == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getPlayerByUUID(ownerUUID);
    }

    public UUID getOwnerUUID() { return ownerUUID; }

    // ── Cosmetic identity ──────────────────────────────────────────────────────

    public void setCosmeticId(String id) { this.cosmeticId = id; }
    public String getCosmeticId()        { return cosmeticId; }

    // ── NBT ────────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUUID != null) tag.putUUID(NBT_OWNER, ownerUUID);
        tag.putString(NBT_COSMETIC, cosmeticId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(NBT_OWNER)) ownerUUID = tag.getUUID(NBT_OWNER);
        cosmeticId = tag.getString(NBT_COSMETIC);
    }

    // ── Misc ───────────────────────────────────────────────────────────────────

    @Override
    protected boolean shouldDropLoot() { return false; }

    @Override
    public boolean removeWhenFarAway(double distToPlayer) { return false; }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                       MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }
}
