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
}
