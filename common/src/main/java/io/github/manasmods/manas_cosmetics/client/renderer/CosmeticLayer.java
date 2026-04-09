package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.api.WeaponTypeChecker;
import io.github.manasmods.manas_cosmetics.client.ClientCosmeticState;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
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

            ResourceLocation texture = getTexture(id, model);

            poseStack.pushPose();
            applySlotTransform(poseStack, slot, player, partialTick);
            applyDefTransform(poseStack, def);

            float animTime = ageInTicks + partialTick;
            BBModelRenderer.render(poseStack, bufferSource, packedLight, model, texture, animTime);

            poseStack.popPose();
        }
    }

    // ── Slot attachment transforms ─────────────────────────────────────────────

    private static void applySlotTransform(PoseStack ps, CosmeticSlot slot, Player player, float pt) {
        switch (slot) {
            case HELMET      -> { ps.translate(0, 0.25, 0); }
            case ABOVE_HEAD  -> { ps.translate(0, 0.9, 0); }
            case CHESTPLATE  -> { ps.translate(0, 0.1, 0); }
            case BACK        -> { ps.translate(0, 0.1, -0.2); }
            case FRONT       -> { ps.translate(0, 0.1,  0.2); }
            case LEGS        -> { ps.translate(0, -0.2, 0); }
            case BOOTS       -> { ps.translate(0, -0.7, 0); }
            case ORBIT       -> applyOrbitTransform(ps, player, pt);
            case PET         -> {} // pet entity handles its own position
            case WEAPON, SHIELD, GRIMOIRE, MAGIC_STAFF -> applyHandTransform(ps, player);
        }
    }

    private static void applyOrbitTransform(PoseStack ps, Player player, float partialTick) {
        float angle = (player.tickCount + partialTick) * 3.0f; // degrees per tick
        double rad = Math.toRadians(angle);
        float radius = 0.8f;
        ps.translate(Math.sin(rad) * radius, 0.5, Math.cos(rad) * radius);
    }

    private static void applyHandTransform(PoseStack ps, Player player) {
        ps.translate(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0.3 : -0.3,
                     -0.4, 0.1);
    }

    private static void applyDefTransform(PoseStack ps, CosmeticDefinition def) {
        float[] s = def.scale();
        float[] o = def.offset();
        ps.translate(o[0] / 16.0, o[1] / 16.0, o[2] / 16.0);
        ps.scale(s[0], s[1], s[2]);
    }

    // ── Texture management ─────────────────────────────────────────────────────

    /** Package-visible so {@link PetCosmeticRenderer} can reuse the same texture cache. */
    static ResourceLocation getTexture(String id, BBModelData model) {
        return TEXTURE_CACHE.computeIfAbsent(id, k -> {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                "manas_cosmetics", "dynamic/" + k.replace(':', '/'));
            if (model.textureBytes().length > 0) {
                uploadTexture(loc, model.textureBytes());
            }
            return loc;
        });
    }

    private static void uploadTexture(ResourceLocation loc, byte[] pngBytes) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            java.io.InputStream stream = new java.io.ByteArrayInputStream(pngBytes);
            net.minecraft.client.renderer.texture.SimpleTexture simple =
                new net.minecraft.client.renderer.texture.SimpleTexture(loc) {
                    @Override
                    public net.minecraft.server.packs.resources.ResourceManager.Empty prepare(
                            net.minecraft.server.packs.resources.ResourceManager rm,
                            java.util.concurrent.Executor exec) {
                        return null;
                    }
                };
            mc.execute(() -> {
                try {
                    com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(stream);
                    DynamicTexture tex = new DynamicTexture(image);
                    mc.getTextureManager().register(loc, tex);
                } catch (Exception e) {
                    // Log and continue — cosmetic will render without texture
                }
            });
        } catch (Exception ignored) {}
    }

    public static void evictTexture(String cosmeticId) {
        if ("*".equals(cosmeticId)) {
            TEXTURE_CACHE.clear();
        } else {
            TEXTURE_CACHE.remove(cosmeticId);
        }
    }
}
