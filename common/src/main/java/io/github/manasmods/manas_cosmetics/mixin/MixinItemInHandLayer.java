package io.github.manasmods.manas_cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.api.WeaponTypeChecker;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Suppresses vanilla third-person held-item rendering on a per-arm basis: each arm's
 * item is hidden only when a cosmetic would actually replace it (matching weapon type
 * or force-equipped / untyped). Items on arms with no matching cosmetic still render.
 */
@Mixin(ItemInHandLayer.class)
public class MixinItemInHandLayer<T extends LivingEntity, M extends EntityModel<T>> {

    @Inject(
        method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void manas_cosmetics$hideArmItemForCosmetic(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;
        if (stack.isEmpty()) return;

        // Cosmetics render at the main arm only, so off-arm items are never suppressed.
        // (ItemInHandLayer pairs main-arm with the main-hand stack, so this is the only path
        //  where suppression can apply.)
        if (arm != player.getMainArm()) return;

        UUID uuid = player.getUUID();
        ClientCosmeticState state = ClientCosmeticState.get();
        ClientCosmeticModelCache cache = ClientCosmeticModelCache.get();

        // 1. Slot-based weapon cosmetics
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!slot.isWeaponSlot()) continue;
            Optional<String> cosmeticId = player.isLocalPlayer()
                ? state.getEquipped(slot)
                : state.getEquippedForPlayer(uuid, slot);
            if (cosmeticId.isEmpty()) continue;

            Optional<CosmeticDefinition> defOpt = cache.getDefinition(cosmeticId.get());
            if (defOpt.isEmpty()) continue;
            CosmeticDefinition def = defOpt.get();

            boolean force = player.isLocalPlayer()
                ? state.isForceEquip(slot)
                : state.isForceEquipForPlayer(uuid, slot);

            if (def.weaponType() == WeaponType.ANY
                    || force
                    || WeaponTypeChecker.matches(stack, def.weaponType())) {
                ci.cancel();
                return;
            }
        }

        // 2. Per-weapon-type cosmetics
        Map<WeaponType, String> weaponMap = player.isLocalPlayer()
            ? state.getAllEquippedWeapon()
            : state.getAllEquippedWeaponForPlayer(uuid);

        for (Map.Entry<WeaponType, String> entry : weaponMap.entrySet()) {
            WeaponType wt = entry.getKey();
            boolean force = player.isLocalPlayer()
                ? state.isForceEquipWeapon(wt)
                : state.isForceEquipWeaponForPlayer(uuid, wt);

            if (force || WeaponTypeChecker.matches(stack, wt)) {
                ci.cancel();
                return;
            }
        }
    }
}
