package io.github.manasmods.manas_cosmetics.neoforge;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(ManasCosmetics.MOD_ID)
public final class ManasCosmeticsNeoForge {
    public ManasCosmeticsNeoForge() {
        ManasCosmetics.init();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            io.github.manasmods.manas_cosmetics.client.ManasCosmticsClient.init();
        }
    }
}
