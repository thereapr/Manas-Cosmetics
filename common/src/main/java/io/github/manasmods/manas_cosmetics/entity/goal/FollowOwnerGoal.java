package io.github.manasmods.manas_cosmetics.entity.goal;

import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Makes the {@link PetCosmeticEntity} follow its owner.
 *
 * - Activates when the pet is more than {@code minDist} blocks from the owner.
 * - Teleports to a random spot near the owner when distance exceeds {@code teleportDist}.
 * - Stops when the pet is within {@code stopDist} blocks.
 */
public final class FollowOwnerGoal extends Goal {

    private static final float MIN_DIST      = 3.0f;
    private static final float STOP_DIST     = 2.0f;
    private static final float TELEPORT_DIST = 12.0f;

    private final PetCosmeticEntity pet;
    private final double speed;
    private int timeToRecalcPath;

    public FollowOwnerGoal(PetCosmeticEntity pet, double speed) {
        this.pet = pet;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = pet.getOwnerEntity();
        if (owner == null || !owner.isAlive()) return false;
        return pet.distanceToSqr(owner) > (double) (MIN_DIST * MIN_DIST);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = pet.getOwnerEntity();
        if (owner == null || !owner.isAlive()) return false;
        if (pet.getNavigation().isDone()) return false;
        return pet.distanceToSqr(owner) > (double) (STOP_DIST * STOP_DIST);
    }

    @Override
    public void start() {
        timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        pet.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity owner = pet.getOwnerEntity();
        if (owner == null) return;

        pet.getLookControl().setLookAt(owner, 10f, pet.getMaxHeadXRot());

        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = 10;

            double distSq = pet.distanceToSqr(owner);

            if (distSq > (double) (TELEPORT_DIST * TELEPORT_DIST)) {
                teleportToOwner(owner);
            } else {
                pet.getNavigation().moveTo(owner, speed);
            }
        }
    }

    private void teleportToOwner(LivingEntity owner) {
        Level level = pet.level();
        // Try a few random spots within 4 blocks of the owner
        for (int attempt = 0; attempt < 10; attempt++) {
            double dx = (pet.getRandom().nextDouble() - 0.5) * 8.0;
            double dz = (pet.getRandom().nextDouble() - 0.5) * 8.0;
            double targetX = owner.getX() + dx;
            double targetY = owner.getY();
            double targetZ = owner.getZ() + dz;
            BlockPos target = BlockPos.containing(targetX, targetY, targetZ);

            // Ensure the block above is clear
            if (level.isEmptyBlock(target) && level.isEmptyBlock(target.above())) {
                pet.moveTo(targetX, targetY, targetZ, pet.getYRot(), pet.getXRot());
                pet.getNavigation().stop();
                return;
            }
        }
        // Fallback: snap directly to owner position
        pet.moveTo(owner.getX(), owner.getY(), owner.getZ(), pet.getYRot(), pet.getXRot());
        pet.getNavigation().stop();
    }
}
