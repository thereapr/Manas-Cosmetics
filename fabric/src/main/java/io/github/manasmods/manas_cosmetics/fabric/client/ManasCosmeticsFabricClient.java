package io.github.manasmods.manas_cosmetics.fabric.client;

import io.github.manasmods.manas_cosmetics.client.ManasCosmticsClient;
import net.fabricmc.api.ClientModInitializer;

public final class ManasCosmeticsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ManasCosmticsClient.init();
    }
}
