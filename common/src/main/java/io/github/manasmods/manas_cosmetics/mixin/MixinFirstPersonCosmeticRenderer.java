package io.github.manasmods.manas_cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.api.WeaponTypeChecker;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import io.github.manasmods.manas_cosmetics.client.renderer.BBModelRenderer;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Hooks into first-person hand rendering to:
 *  1. Render the weapon cosmetic BBModel in first-person view.
 *  2. Suppress the vanilla held item when a weapon cosmetic is equipped so
 *     the real item doesn't show through the cosmetic.
 */
@Mixin(ItemInHandRenderer.class)
public class MixinFirstPersonCosmeticRenderer {

    /** Renders the weapon cosmetic after the vanilla hands have been drawn. */
    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void manas_cosmetics$renderFirstPersonWeaponCosmetic(
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {

        ClientCosmeticState state = ClientCosmeticState.get();
        ClientCosmeticModelCache cache = ClientCosmeticModelCache.get();

        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) continue;
            Optional<String> cosmeticId = state.getEquipped(slot);
            if (cosmeticId.isEmpty()) continue;

            String id = cosmeticId.get();
            Optional<CosmeticDefinition> defOpt = cache.getDefinition(id);
            Optional<BBModelData> modelOpt = cache.getModel(id);
            if (defOpt.isEmpty() || modelOpt.isEmpty()) continue;

            CosmeticDefinition def = defOpt.get();
            BBModelData model = modelOpt.get();

            if (def.weaponType() != WeaponType.ANY) {
                ItemStack heldItem = player.getMainHandItem();
                if (!WeaponTypeChecker.matches(heldItem, def.weaponType())) continue;
            }

            ResourceLocation texture = CosmeticLayer.getOrUploadTexture(id, model);
            float animTime = (player.tickCount + partialTick) / 20.0f;

            poseStack.pushPose();
            // Place model at right-arm position (matches ItemInHandRenderer.applyItemArmTransform)
            poseStack.translate(0.56f, -0.52f, -0.72f);
            // handheld.json firstperson_righthand: Y=-90, Z=+25 in standard (non-inverted) space
            poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(-90f)));
            poseStack.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(25f)));
            float[] s = def.scale();
            poseStack.scale(s[0], s[1], s[2]);
            BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);
            poseStack.popPose();
            break;
        }
    }

    /**
     * Suppresses vanilla item rendering for first-person contexts when a weapon cosmetic
     * is equipped. The arm still renders; only the held item mesh is skipped.
     */
    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void manas_cosmetics$hideVanillaFirstPersonItem(
            LivingEntity entity,
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            CallbackInfo ci) {

        if (displayContext != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && displayContext != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) return;
        if (!(entity instanceof LocalPlayer)) return;

        ClientCosmeticState state = ClientCosmeticState.get();
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) continue;
            if (state.getEquipped(slot).isPresent()) {
                ci.cancel();
                return;
            }
        }
    }
}
