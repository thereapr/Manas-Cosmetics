package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * Renders a {@link PetCosmeticEntity} by looking up the equipped cosmetic's
 * {@link BBModelData} from the client-side {@link ClientCosmeticModelCache}
 * and drawing it with {@link BBModelRenderer}.
 *
 * The model is rendered at its natural Blockbench scale; only the {@code scale}
 * field from the cosmetic's JSON config is applied (no auto-shrink).
 */
public final class PetCosmeticRenderer extends EntityRenderer<PetCosmeticEntity> {

    /** Maximum dimensions of a rendered pet: 1 block tall, 2 blocks wide/deep. */
    private static final float MAX_HEIGHT_BLOCKS = 1.0f;
    private static final float MAX_WIDTH_BLOCKS  = 2.0f;
    /** Same limits expressed in BBModel units (1 block = 16 units). */
    private static final float MAX_HEIGHT_UNITS = MAX_HEIGHT_BLOCKS * 16f;
    private static final float MAX_WIDTH_UNITS  = MAX_WIDTH_BLOCKS  * 16f;
    private static final float UNITS_TO_BLOCKS = 1f / 16f;

    public PetCosmeticRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PetCosmeticEntity entity) {
        // Unused — texture comes from the BBModel data itself
        return ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "dynamic/pet_fallback");
    }

    @Override
    public void render(PetCosmeticEntity entity,
                       float entityYaw,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight) {

        String cosmeticId = entity.getCosmeticId();
        if (cosmeticId == null || cosmeticId.isEmpty()) return;

        ClientCosmeticModelCache cache = ClientCosmeticModelCache.get();
        Optional<CosmeticDefinition> defOpt = cache.getDefinition(cosmeticId);
        if (defOpt.isEmpty()) return;

        CosmeticDefinition def = defOpt.get();

        // ── Vanilla mob rendering path ─────────────────────────────────────────
        if (def.mobType() != null && !def.mobType().isEmpty()) {
            MobPetRenderer.render(entity, def.mobType(), entityYaw, partialTick,
                    poseStack, bufferSource, packedLight);
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }

        // ── BBModel rendering path ─────────────────────────────────────────────
        Optional<BBModelData> modelOpt = cache.getModel(cosmeticId);
        if (modelOpt.isEmpty()) return;

        BBModelData model = modelOpt.get();

        ResourceLocation texture = CosmeticLayer.getOrUploadTexture(cosmeticId, model);

        poseStack.pushPose();

        // Rotate to face the entity's look direction (interpolated yaw).
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(180f - bodyYaw)));

        // Apply only the user-defined scale (offset and rotation ignored for pet slot).
        float[] s = def.scale();
        poseStack.scale(s[0], s[1], s[2]);

        float animTime = (entity.tickCount + partialTick) / 20.0f;
        BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);

        poseStack.popPose();

        // Shadow / name-tag rendering from parent
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // ── Auto-scale computation ─────────────────────────────────────────────────

    /**
     * Computes a uniform scale factor that fits the model within the pet size envelope:
     * at most {@value #MAX_HEIGHT_BLOCKS} block tall, {@value #MAX_WIDTH_BLOCKS} blocks wide,
     * and {@value #MAX_WIDTH_BLOCKS} blocks deep.  The model is never upscaled.
     */
    private static float computeAutoScale(BBModelData model) {
        float[] minMax = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BBModelData.Bone bone : model.bones()) {
            expandBounds(minMax, bone);
        }
        if (minMax[1] == Float.MAX_VALUE) return 1.0f; // No cubes found

        float height = minMax[4] - minMax[1]; // Y extent (BBModel units)
        float width  = minMax[3] - minMax[0]; // X extent
        float depth  = minMax[5] - minMax[2]; // Z extent

        // Per-axis scale to fit within the envelope
        float scaleH = height > 0 ? MAX_HEIGHT_UNITS / height : 1.0f;
        float scaleW = width  > 0 ? MAX_WIDTH_UNITS  / width  : 1.0f;
        float scaleD = depth  > 0 ? MAX_WIDTH_UNITS  / depth  : 1.0f;

        // Use the most restrictive axis; never upscale
        return Math.min(1.0f, Math.min(scaleH, Math.min(scaleW, scaleD)));
    }

    private static void expandBounds(float[] b, BBModelData.Bone bone) {
        for (BBModelData.Cube cube : bone.cubes()) {
            for (int i = 0; i < 3; i++) {
                float lo = Math.min(cube.from()[i], cube.to()[i]);
                float hi = Math.max(cube.from()[i], cube.to()[i]);
                b[i]     = Math.min(b[i], lo);
                b[i + 3] = Math.max(b[i + 3], hi);
            }
        }
        for (BBModelData.Bone child : bone.children()) {
            expandBounds(b, child);
        }
    }

    // ── Transform helpers ──────────────────────────────────────────────────────

    private static void applyDefTransformWithAutoScale(PoseStack ps,
                                                       CosmeticDefinition def,
                                                       float autoScale) {
        float[] s = def.scale();
        float[] o = def.offset();
        float[] r = def.rotation();

        ps.translate(o[0] * UNITS_TO_BLOCKS, o[1] * UNITS_TO_BLOCKS, o[2] * UNITS_TO_BLOCKS);
        // Combine user-defined scale with auto-scale
        ps.scale(s[0] * autoScale, s[1] * autoScale, s[2] * autoScale);
        if (r[0] != 0) ps.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(r[0])));
        if (r[1] != 0) ps.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(r[1])));
        if (r[2] != 0) ps.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(r[2])));
    }
}
