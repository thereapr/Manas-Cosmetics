package io.github.manasmods.manas_cosmetics.mixin.client;

import io.github.manasmods.manas_cosmetics.client.renderer.ClientCosmeticModelCache;
import io.github.manasmods.manas_cosmetics.client.renderer.CosmeticLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects {@link CosmeticLayer} into every PlayerRenderer instance (both default and slim skin).
 */
@Mixin(PlayerRenderer.class)
public abstract class MixinPlayerRenderer {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void manas_cosmetics$addCosmeticLayer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        PlayerRenderer self = (PlayerRenderer) (Object) this;
        self.addLayer(new CosmeticLayer<>(self, ClientCosmeticModelCache.get()));
    }
}
