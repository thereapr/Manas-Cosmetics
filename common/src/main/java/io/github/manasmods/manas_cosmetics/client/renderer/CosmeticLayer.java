package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.api.WeaponTypeChecker;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link RenderLayer} that draws cosmetics on top of the player model.
 *
 * For each player, it:
 * 1. Looks up which cosmetics are equipped in {@link ClientCosmeticState}.
 * 2. For each equipped cosmetic, fetches the in-memory {@link BBModelData} from
 *    {@link io.github.manasmods.manas_cosmetics.core.CosmeticManager} (client-side copy).
 * 3. Translates/rotates the PoseStack to the correct slot attachment point.
 * 4. Delegates the actual draw call to {@link BBModelRenderer}.
 *
 * GeckoLib's animation system is driven by the server tick counter sent in the
 * sync payload; this layer interpolates between keyframes on the client.
 */
public final class CosmeticLayer<T extends Player, M extends EntityModel<T>>
    extends RenderLayer<T, M> {

    /** Dynamic textures uploaded from the decoded .bbmodel base64, keyed by cosmetic id. */
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new ConcurrentHashMap<>();

    private final ClientCosmeticModelCache modelCache;

    public CosmeticLayer(RenderLayerParent<T, M> renderer, ClientCosmeticModelCache modelCache) {
        super(renderer);
        this.modelCache = modelCache;
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       T player,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {

        UUID uuid = player.getUUID();
        ClientCosmeticState state = ClientCosmeticState.get();

        for (CosmeticSlot slot : CosmeticSlot.values()) {
            Optional<String> cosmeticId = player.isLocalPlayer()
                ? state.getEquipped(slot)
                : state.getEquippedForPlayer(uuid, slot);

            if (cosmeticId.isEmpty()) continue;

            // PET cosmetics are rendered by PetCosmeticRenderer on a separate entity,
            // not as a player attachment layer. Rendering here would place the model
            // at the player's root with no transform, producing a white blocky overlay.
            if (slot == CosmeticSlot.PET) continue;

            // AURA cosmetics use AuraRenderer with procedurally generated geometry.
            // They have no BBModel, so skip the model lookup entirely.
            if (slot == CosmeticSlot.AURA) {
                poseStack.pushPose();
                AuraRenderer.render(poseStack, bufferSource, packedLight, ageInTicks / 20.0f);
                poseStack.popPose();
                continue;
            }

            String id = cosmeticId.get();
            Optional<CosmeticDefinition> defOpt = modelCache.getDefinition(id);
            Optional<BBModelData> modelOpt = modelCache.getModel(id);
            if (defOpt.isEmpty() || modelOpt.isEmpty()) continue;

            CosmeticDefinition def = defOpt.get();
            BBModelData model = modelOpt.get();

            // Weapon-slot visibility: hide unless holding the correct weapon type (or force-equip)
            if (slot.isWeaponSlot() && def.weaponType() != WeaponType.ANY) {
                boolean force = player.isLocalPlayer()
                    ? state.isForceEquip(slot)
                    : state.isForceEquipForPlayer(uuid, slot);
                if (!force) {
                    ItemStack heldItem = player.getMainHandItem();
                    if (!WeaponTypeChecker.matches(heldItem, def.weaponType())) {
                        continue;
                    }
                }
            }

            ResourceLocation texture = getOrUploadTexture(id, model);

            poseStack.pushPose();
            applySlotTransform(poseStack, slot, player, partialTick, this.getParentModel());
            // Weapon slots: only scale is user-configurable — position and orientation are
            // locked to the hand by applyHandTransform so every weapon model sits in the hand.
            if (slot.isWeaponSlot()) {
                float[] s = def.scale();
                poseStack.scale(s[0], s[1], s[2]);
            } else {
                applyDefTransform(poseStack, def);
            }

            // Keyframe times in .bbmodel are in seconds; ageInTicks is in ticks (20/s).
            // Divide by 20 so animation speed matches the authored Blockbench timeline.
            float animTime = ageInTicks / 20.0f;
            BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);

            poseStack.popPose();
        }
    }

    // ── Slot attachment transforms ─────────────────────────────────────────────

    private static void applySlotTransform(PoseStack ps, CosmeticSlot slot, Player player, float pt, EntityModel<?> parentModel) {
        // In the render-layer pose stack, +Z is the player's back (matches vanilla ElytraLayer).
        switch (slot) {
            case HELMET -> {
                // Attach to the head so the cosmetic rotates with head yaw/pitch.
                if (parentModel instanceof HumanoidModel<?> humanoid) {
                    humanoid.head.translateAndRotate(ps);
                }
                ps.translate(0, 0.25, 0);
            }
            case ABOVE_HEAD -> {
                // Attach to the head so the cosmetic rotates with head yaw/pitch.
                if (parentModel instanceof HumanoidModel<?> humanoid) {
                    humanoid.head.translateAndRotate(ps);
                }
                ps.translate(0, 0.9, 0);
            }
            case EARS -> {
                // Attach to the head centre so the cosmetic rotates with head yaw/pitch.
                // The bbmodel defines both ears symmetrically around the head centre.
                if (parentModel instanceof HumanoidModel<?> humanoid) {
                    humanoid.head.translateAndRotate(ps);
                }
                // No additional Y offset – ear models position themselves relative to head centre
            }
            case CHESTPLATE  -> ps.translate(0,  0.1,   0);
            case BACK        -> ps.translate(0,  0.0,   0.125); // vanilla elytra anchor
            case FRONT       -> ps.translate(0,  0.1,  -0.2);
            case LEGS        -> ps.translate(0, -0.2,   0);
            case BOOTS       -> ps.translate(0, -0.7,   0);
            case ORBIT       -> applyOrbitTransform(ps, player, pt);
            case PET         -> {}  // pet entity handles its own position
            case AURA        -> {}  // aura is handled before this switch; never reached
            case WEAPON, SHIELD, GRIMOIRE, MAGIC_STAFF -> applyHandTransform(ps, player, parentModel);
        }
    }

    private static void applyOrbitTransform(PoseStack ps, Player player, float partialTick) {
        float angle = (player.tickCount + partialTick) * 3.0f; // degrees per tick
        double rad = Math.toRadians(angle);
        float radius = 0.8f;
        ps.translate(Math.sin(rad) * radius, 0.5, Math.cos(rad) * radius);
    }

    /**
     * Positions the weapon cosmetic in the player's hand with the blade pointing the same
     * direction as a vanilla held sword.
     *
     * The render-layer pose stack is post scale(-1,-1,1), so +Y in this space points toward
     * the feet (away from head). The arm pivot is at the shoulder; the hand (10 model units
     * below the pivot) is therefore at +0.625 in this space.
     *
     * Rotation replicates vanilla item/handheld.json thirdperson_righthand:
     *   Y=-90°, Z=+125° (right hand); mirrored for left hand.
     */
    private static void applyHandTransform(PoseStack ps, Player player, EntityModel<?> parentModel) {
        if (parentModel instanceof HumanoidModel<?> humanoid) {
            boolean isRight = player.getMainArm() == HumanoidArm.RIGHT;
            ModelPart arm = isRight ? humanoid.rightArm : humanoid.leftArm;
            arm.translateAndRotate(ps);
            float sign = isRight ? 1f : -1f;
            // +Y is toward the feet in the post-scale(-1,-1,1) render space, so +0.625
            // reaches the hand (10 model units below the shoulder pivot).
            ps.translate(sign / 16f, 0.625f, 0f);
            // Vanilla thirdperson_righthand: Y=-90, Z=+55. In the post-scale(-1,-1,1) render
            // space +Y points toward feet, so Z=+55 tilts blade down; Z=+125 (=180-55) tilts
            // blade up, matching the iron sword's blade-up appearance.
            // X=+90 rolls the blade 90° around its length axis so the edge faces up/down
            // rather than left/right.
            ps.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(sign * -90f)));
            ps.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(sign * 125f)));
            ps.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(90f)));
        } else {
            // Fallback for non-humanoid models
            ps.translate(player.getMainArm() == HumanoidArm.RIGHT ? 0.3 : -0.3, -0.4, 0.1);
        }
    }

    private static void applyDefTransform(PoseStack ps, CosmeticDefinition def) {
        float[] s = def.scale();
        float[] o = def.offset();
        float[] r = def.rotation();
        ps.translate(o[0] / 16.0, o[1] / 16.0, o[2] / 16.0);
        ps.scale(s[0], s[1], s[2]);
        if (r[0] != 0) ps.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(r[0])));
        if (r[1] != 0) ps.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(r[1])));
        if (r[2] != 0) ps.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(r[2])));
    }

    // ── Texture management ─────────────────────────────────────────────────────

    /** Package-visible so {@link PetCosmeticRenderer} can share the same cache. */
    static ResourceLocation getOrUploadTexture(String id, BBModelData model) {
        return TEXTURE_CACHE.computeIfAbsent(id, k -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                "manas_cosmetics", "dynamic/" + k.replace(':', '/'));
            if (model.textureBytes().length > 0) {
                uploadTexture(loc, model.textureBytes());
            } else {
                uploadFallbackTexture(loc);
            }
            return loc;
        });
    }

    private static void uploadTexture(ResourceLocation loc, byte[] pngBytes) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            try {
                java.io.InputStream stream = new java.io.ByteArrayInputStream(pngBytes);
                com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(stream);
                DynamicTexture tex = new DynamicTexture(image);
                mc.getTextureManager().register(loc, tex);
            } catch (Exception e) {
                // PNG parse failed — fall back to a 1×1 white texture so the mesh is still visible
                uploadFallbackTexture(loc);
            }
        });
    }

    /** Registers a 1×1 opaque-white texture so the mesh renders rather than crashing. */
    private static void uploadFallbackTexture(ResourceLocation loc) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            com.mojang.blaze3d.platform.NativeImage image =
                new com.mojang.blaze3d.platform.NativeImage(1, 1, false);
            image.setPixelRGBA(0, 0, 0xFFFFFFFF);
            mc.getTextureManager().register(loc, new DynamicTexture(image));
        });
    }

    public static void evictTexture(String cosmeticId) {
        if ("*".equals(cosmeticId)) {
            TEXTURE_CACHE.clear();
        } else {
            TEXTURE_CACHE.remove(cosmeticId);
        }
    }
}
