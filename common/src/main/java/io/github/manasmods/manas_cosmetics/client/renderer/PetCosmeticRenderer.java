package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a {@link PetCosmeticEntity} using the BBModel stored in {@link ClientCosmeticModelCache}.
 *
 * The cosmetic ID is taken from the entity and looked up in the client-side model cache,
 * which is populated when the server sends the registry sync packet on login.
 */
public class PetCosmeticRenderer extends EntityRenderer<PetCosmeticEntity> {

    public PetCosmeticRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PetCosmeticEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        String cosmeticId = entity.getCosmeticId();
        if (cosmeticId == null || cosmeticId.isEmpty()) return;

        ClientCosmeticModelCache cache = ClientCosmeticModelCache.get();
        var modelOpt = cache.getModel(cosmeticId);
        if (modelOpt.isEmpty()) return;

        BBModelData model = modelOpt.get();
        ResourceLocation texture = CosmeticLayer.getTexture(cosmeticId, model);

        poseStack.pushPose();
        float animTime = entity.tickCount + partialTick;
        BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PetCosmeticEntity entity) {
        // Texture is managed dynamically per-cosmetic inside BBModelRenderer / CosmeticLayer
        return ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "textures/entity/empty.png");
    }
}
