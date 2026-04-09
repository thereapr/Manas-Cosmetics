package io.github.manasmods.manas_cosmetics.fabric;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import net.fabricmc.api.ModInitializer;

public final class ManasCosmeticsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ManasCosmetics.init();
    }
}
