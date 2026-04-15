package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a pet cosmetic by delegating to the vanilla {@link EntityRenderer} of the
 * target mob type, scaled uniformly so the mob's hitbox height equals exactly 1 block.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>A <em>display mob</em> of the target vanilla type is created once per mob-type
 *       and cached (per-level, so dimension changes are handled automatically).
 *       It is never added to the world — it is purely a rendering proxy.</li>
 *   <li>Each frame, orientation and walk-animation state are mirrored from the live
 *       {@link PetCosmeticEntity} to the display mob so all vanilla animations
 *       (walking, idle, head-turning) play correctly without the display mob being
 *       ticked by the world.</li>
 *   <li>The PoseStack is uniformly scaled by {@code 1 / mobHeight} before delegating
 *       to the vanilla renderer, producing a 1-block-tall result regardless of how
 *       large the source mob normally is.</li>
 * </ol>
 *
 * <h3>Animation notes</h3>
 * Walk animations are driven by {@code WalkAnimationState} which tracks a running
 * "position" (phase) and "speed" value updated once per tick.  Because the display
 * mob is never ticked, these private fields are copied from the pet entity each frame
 * via reflection.  All other per-frame animation inputs (tickCount, body/head yaw)
 * are public fields and are simply assigned directly.  The net result is that leg
 * swinging, idle bobs, and any other vanilla animations behave identically to the
 * full-size mob — just rendered smaller.
 */
@SuppressWarnings("unchecked")
public final class MobPetRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("manas_cosmetics");

    /**
     * One display mob per target mob-type ID.
     * The cache key includes a System identity hash of the current ClientLevel so
     * stale entries are detected and replaced when the player changes dimension.
     */
    private static final Map<String, Mob> DISPLAY_MOB_CACHE = new HashMap<>();

    // ── WalkAnimationState reflection ──────────────────────────────────────────
    // WalkAnimationState stores walk phase and speed in four private float fields.
    // We mirror them from the live pet entity to the display mob each frame.

    private static final Field WALK_SPEED_OLD;
    private static final Field WALK_SPEED;
    private static final Field WALK_POS_OLD;
    private static final Field WALK_POS;

    static {
        Field so = null, s = null, po = null, p = null;
        try {
            Class<?> c = WalkAnimationState.class;
            so = c.getDeclaredField("speedOld");
            s  = c.getDeclaredField("speed");
            po = c.getDeclaredField("positionOld");
            p  = c.getDeclaredField("position");
            so.setAccessible(true);
            s.setAccessible(true);
            po.setAccessible(true);
            p.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOGGER.warn("[manas_cosmetics] WalkAnimationState reflection failed – walk animation will not sync. "
                + "This usually means an incompatible MC version. Error: {}", e.getMessage());
        }
        WALK_SPEED_OLD = so;
        WALK_SPEED     = s;
        WALK_POS_OLD   = po;
        WALK_POS       = p;
    }

    private MobPetRenderer() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Renders the pet using the vanilla renderer for {@code mobTypeId}, scaled so the
     * mob's collision-box height equals 1 block.
     *
     * @param entity      the live pet entity (provides position / animation state)
     * @param mobTypeId   registry key of the target mob, e.g. {@code "minecraft:pig"}
     * @param entityYaw   interpolated body yaw in degrees (passed through to the vanilla renderer)
     * @param partialTick render partial tick (0–1)
     */
    public static void render(
            PetCosmeticEntity entity,
            String mobTypeId,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {

        Mob displayMob = getOrCreateDisplayMob(mobTypeId);
        if (displayMob == null) return;

        syncState(entity, displayMob);

        // Scale so the mob's hitbox height maps to exactly 1 block
        float mobHeight = displayMob.getType().getHeight();
        float scale = mobHeight > 0.001f ? 1.0f / mobHeight : 1.0f;

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);

        EntityRenderer<Mob> renderer =
            (EntityRenderer<Mob>) Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(displayMob);

        // Call the renderer directly (not via dispatcher) to avoid double-shadow
        // rendering and name-tag logic that belongs to the pet entity, not the proxy.
        renderer.render(displayMob, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    /**
     * Clears all cached display mobs.  Must be called when the client disconnects to
     * avoid holding stale references to the old ClientLevel.
     */
    public static void clearCache() {
        DISPLAY_MOB_CACHE.clear();
    }

    // ── Display mob management ─────────────────────────────────────────────────

    private static Mob getOrCreateDisplayMob(String mobTypeId) {
        Level currentLevel = Minecraft.getInstance().level;
        if (currentLevel == null) return null;

        // Embed the level's identity into the cache key so that entries created in
        // a different dimension (or after a reconnect) are replaced automatically.
        String cacheKey = mobTypeId + "@" + System.identityHashCode(currentLevel);

        Mob cached = DISPLAY_MOB_CACHE.get(cacheKey);
        if (cached != null) return cached;

        // Evict any stale entry for this mob type (different level instance)
        DISPLAY_MOB_CACHE.entrySet().removeIf(e -> e.getKey().startsWith(mobTypeId + "@"));

        Mob newMob = createDisplayMob(mobTypeId, currentLevel);
        if (newMob != null) {
            DISPLAY_MOB_CACHE.put(cacheKey, newMob);
        }
        return newMob;
    }

    private static Mob createDisplayMob(String mobTypeId, Level level) {
        ResourceLocation id = ResourceLocation.tryParse(mobTypeId);
        if (id == null) {
            LOGGER.warn("[manas_cosmetics] Invalid mob_type for pet: '{}'", mobTypeId);
            return null;
        }

        return BuiltInRegistries.ENTITY_TYPE
            .getOptional(id)
            .map(type -> {
                Entity e = type.create(level);
                if (e instanceof Mob mob) return mob;
                if (e != null) e.discard();
                LOGGER.warn("[manas_cosmetics] mob_type '{}' is not a Mob – ignoring", mobTypeId);
                return null;
            })
            .orElseGet(() -> {
                LOGGER.warn("[manas_cosmetics] Unknown mob_type for pet: '{}'", mobTypeId);
                return null;
            });
    }

    // ── State synchronisation ──────────────────────────────────────────────────

    /**
     * Mirrors the animation-relevant state from the live {@link PetCosmeticEntity}
     * to the display mob so vanilla model {@code setupAnim()} produces the correct
     * joint angles each frame.
     *
     * <p>Public fields are assigned directly; the private {@link WalkAnimationState}
     * fields are copied via the reflection handles established at class-load time.</p>
     */
    private static void syncState(PetCosmeticEntity source, Mob target) {
        target.tickCount = source.tickCount;
        target.yBodyRot  = source.yBodyRot;
        target.yBodyRotO = source.yBodyRotO;
        target.yHeadRot  = source.yHeadRot;
        target.yHeadRotO = source.yHeadRotO;
        copyWalkAnimation(source.walkAnimation, target.walkAnimation);
    }

    private static void copyWalkAnimation(WalkAnimationState from, WalkAnimationState to) {
        if (WALK_SPEED_OLD == null) return; // reflection setup failed; skip silently
        try {
            WALK_SPEED_OLD.set(to, WALK_SPEED_OLD.get(from));
            WALK_SPEED    .set(to, WALK_SPEED    .get(from));
            WALK_POS_OLD  .set(to, WALK_POS_OLD  .get(from));
            WALK_POS      .set(to, WALK_POS      .get(from));
        } catch (IllegalAccessException e) {
            // Silently skip – legs may look static but nothing will crash
        }
    }
}
