package io.github.manasmods.manas_cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla held-item rendering for players who have a weapon-slot cosmetic equipped.
 * The cosmetic BBModel replaces the visual entirely, so the real item beneath must be hidden.
 */
@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer<T extends LivingEntity, M extends EntityModel<T>> {

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void manas_cosmetics$hideWeaponForCosmetic(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            T entity,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        ClientCosmeticState state = ClientCosmeticState.get();
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) continue;
            boolean equipped = player.isLocalPlayer()
                ? state.getEquipped(slot).isPresent()
                : state.getEquippedForPlayer(player.getUUID(), slot).isPresent();
            if (equipped) {
                ci.cancel();
                return;
            }
        }
    }
}
