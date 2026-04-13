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
 * <p>Auto-scaling: the model is uniformly scaled so its height is at most
 * {@value #MAX_HEIGHT_BLOCKS} blocks tall, preventing oversized pets.</p>
 */
public final class PetCosmeticRenderer extends EntityRenderer<PetCosmeticEntity> {

    /** Maximum height of a rendered pet in blocks. */
    private static final float MAX_HEIGHT_BLOCKS = 1.0f;
    /** In BBModel units (1 block = 16 units). */
    private static final float MAX_HEIGHT_UNITS = MAX_HEIGHT_BLOCKS * 16f;
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
        Optional<BBModelData> modelOpt = cache.getModel(cosmeticId);
        if (defOpt.isEmpty() || modelOpt.isEmpty()) return;

        CosmeticDefinition def = defOpt.get();
        BBModelData model = modelOpt.get();

        ResourceLocation texture = CosmeticLayer.getOrUploadTexture(cosmeticId, model);

        poseStack.pushPose();

        // Rotate to face the entity's look direction (interpolated yaw).
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(180f - bodyYaw)));

        // Auto-scale the model so its vertical extent fits within MAX_HEIGHT_BLOCKS.
        float autoScale = computeAutoScale(model);

        // Apply user-defined scale/offset/rotation from the cosmetic definition on top.
        applyDefTransformWithAutoScale(poseStack, def, autoScale);

        float animTime = entity.tickCount + partialTick;
        BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);

        poseStack.popPose();

        // Shadow / name-tag rendering from parent
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    // ── Auto-scale computation ─────────────────────────────────────────────────

    /**
     * Computes a uniform scale factor so the model's height fits within
     * {@value #MAX_HEIGHT_UNITS} BBModel units.
     */
    private static float computeAutoScale(BBModelData model) {
        float[] minMax = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                          -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BBModelData.Bone bone : model.bones()) {
            expandBounds(minMax, bone);
        }
        if (minMax[1] == Float.MAX_VALUE) return 1.0f; // No cubes found
        float height = minMax[4] - minMax[1]; // height in BBModel units
        if (height <= 0 || height <= MAX_HEIGHT_UNITS) return 1.0f;
        return MAX_HEIGHT_UNITS / height;
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
