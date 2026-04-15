package io.github.manasmods.manas_cosmetics.entity;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class EntityRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ManasCosmetics.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<PetCosmeticEntity>> PET_COSMETIC =
        ENTITIES.register("pet_cosmetic", () ->
            EntityType.Builder.<PetCosmeticEntity>of(PetCosmeticEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.0f)  // height ≥ 1 block prevents sinking through half-block gaps
                .noSummon()
                .noSave()   // Pets are re-spawned from player NBT on login; no world-save needed
                .clientTrackingRange(10)
                .build("manas_cosmetics:pet_cosmetic")
        );

    public static void register() {
        ENTITIES.register();
    }
}
