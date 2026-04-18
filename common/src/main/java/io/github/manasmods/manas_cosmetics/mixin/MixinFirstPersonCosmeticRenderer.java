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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;

/**
 * Hooks into first-person hand rendering to:
 *  1. Render the weapon cosmetic BBModel in first-person view.
 *  2. Suppress the vanilla held item when a matching weapon cosmetic is equipped so
 *     the real item doesn't show through the cosmetic.
 *
 * Handles both equipping paths:
 *   - Slot-based:   ClientCosmeticState.equipped      (CosmeticSlot -> id)
 *   - Per-weapon:   ClientCosmeticState.equippedWeapon (WeaponType -> id, used by wardrobe)
 */
@Mixin(ItemInHandRenderer.class)
public class MixinFirstPersonCosmeticRenderer {

    /** Renders weapon cosmetics after the vanilla hands have been drawn. */
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
        ItemStack heldMain = player.getMainHandItem();
        boolean isRight = player.getMainArm() == HumanoidArm.RIGHT;
        float animTime = (player.tickCount + partialTick) / 20.0f;

        // 1. Slot-based weapon cosmetics (WEAPON / SHIELD / GRIMOIRE / MAGIC_STAFF)
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) continue;
            Optional<String> cosmeticId = state.getEquipped(slot);
            if (cosmeticId.isEmpty()) continue;

            String id = cosmeticId.get();
            Optional<CosmeticDefinition> defOpt = cache.getDefinition(id);
            Optional<BBModelData> modelOpt = cache.getModel(id);
            if (defOpt.isEmpty() || modelOpt.isEmpty()) continue;

            CosmeticDefinition def = defOpt.get();
            if (def.weaponType() != WeaponType.ANY && !state.isForceEquip(slot)
                    && !WeaponTypeChecker.matches(heldMain, def.weaponType())) continue;

            renderBBModelInHand(poseStack, bufferSource, packedLight, id, modelOpt.get(),
                                def, isRight, animTime, false);
        }

        // 2. Per-weapon-type cosmetics (wardrobe-equipped by WeaponType)
        for (Map.Entry<WeaponType, String> entry : state.getAllEquippedWeapon().entrySet()) {
            WeaponType wt = entry.getKey();
            String id = entry.getValue();
            Optional<CosmeticDefinition> defOpt = cache.getDefinition(id);
            Optional<BBModelData> modelOpt = cache.getModel(id);
            if (defOpt.isEmpty() || modelOpt.isEmpty()) continue;

            if (!state.isForceEquipWeapon(wt) && !WeaponTypeChecker.matches(heldMain, wt)) continue;

            renderBBModelInHand(poseStack, bufferSource, packedLight, id, modelOpt.get(),
                                defOpt.get(), isRight, animTime, true);
        }
    }

    /**
     * Places the model at the main-hand first-person anchor (matches vanilla
     * {@code ItemInHandRenderer.applyItemArmTransform} for a fully-equipped item)
     * and applies the cosmetic definition's transform.
     *
     * @param fullDefTransform when {@code true}, applies offset+rotation+scale from the
     *                         definition (per-weapon-type path). When {@code false}, only
     *                         scale is applied (matches slot-based behaviour in {@link CosmeticLayer}).
     */
    private static void renderBBModelInHand(
            PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource,
            int packedLight,
            String id,
            BBModelData model,
            CosmeticDefinition def,
            boolean isRight,
            float animTime,
            boolean fullDefTransform) {

        ResourceLocation texture = CosmeticLayer.getOrUploadTexture(id, model);
        float sign = isRight ? 1f : -1f;

        poseStack.pushPose();
        // Main-hand anchor (vanilla applyItemArmTransform): +/-0.56 X depending on main arm.
        poseStack.translate(sign * 0.56f, -0.52f, -0.72f);
        // handheld.json firstperson_{right,left}hand base orientation.
        poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(sign * -90f)));
        poseStack.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(sign * 25f)));

        float[] s = def.scale();
        if (fullDefTransform) {
            float[] o = def.offset();
            float[] r = def.rotation();
            poseStack.translate(o[0] / 16.0, o[1] / 16.0, o[2] / 16.0);
            if (r[0] != 0) poseStack.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(r[0])));
            if (r[1] != 0) poseStack.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(r[1])));
            if (r[2] != 0) poseStack.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(r[2])));
        }
        poseStack.scale(s[0], s[1], s[2]);

        BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);
        poseStack.popPose();
    }

    /**
     * Suppresses vanilla item rendering for first-person contexts when a weapon cosmetic
     * replaces the held item. The arm still renders; only the item mesh is skipped.
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
        ClientCosmeticModelCache cache = ClientCosmeticModelCache.get();

        // Slot-based: suppress only when the held item actually matches the cosmetic's weapon type
        // (or the cosmetic is untyped / force-equipped).
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) continue;
            Optional<String> cosmeticId = state.getEquipped(slot);
            if (cosmeticId.isEmpty()) continue;

            Optional<CosmeticDefinition> defOpt = cache.getDefinition(cosmeticId.get());
            if (defOpt.isEmpty()) continue;
            CosmeticDefinition def = defOpt.get();

            if (def.weaponType() == WeaponType.ANY
                    || state.isForceEquip(slot)
                    || WeaponTypeChecker.matches(itemStack, def.weaponType())) {
                ci.cancel();
                return;
            }
        }

        // Per-weapon-type: suppress the item whose type matches any equipped weapon cosmetic.
        for (Map.Entry<WeaponType, String> entry : state.getAllEquippedWeapon().entrySet()) {
            WeaponType wt = entry.getKey();
            if (state.isForceEquipWeapon(wt) || WeaponTypeChecker.matches(itemStack, wt)) {
                ci.cancel();
                return;
            }
        }
    }
}
