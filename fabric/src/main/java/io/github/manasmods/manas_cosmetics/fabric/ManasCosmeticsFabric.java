package io.github.manasmods.manas_cosmetics.fabric;

import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.entity.EntityRegistry;
import io.github.manasmods.manas_cosmetics.entity.PetCosmeticEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class ManasCosmeticsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ManasCosmetics.init();

        // Register entity attribute defaults for Fabric
        FabricDefaultAttributeRegistry.register(
            EntityRegistry.PET_COSMETIC.get(),
            PetCosmeticEntity.createAttributes()
        );
    }
}
