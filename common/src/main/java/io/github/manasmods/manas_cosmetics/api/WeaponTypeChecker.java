package io.github.manasmods.manas_cosmetics.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Determines whether a held {@link ItemStack} matches a {@link WeaponType}.
 *
 * Matching priority:
 *  1. {@code WeaponType.ANY}  → always matches
 *  2. Item tag lookup:  {@code manas_cosmetics:weapon_type/<type_id>}
 *     (populated by vanilla entries + other weapon mods)
 */
public final class WeaponTypeChecker {

    private WeaponTypeChecker() {}

    public static boolean matches(ItemStack stack, WeaponType type) {
        if (type == WeaponType.ANY) return true;
        if (stack.isEmpty()) return false;

        TagKey<net.minecraft.world.item.Item> tag = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("manas_cosmetics", "weapon_type/" + type.getId())
        );
        return stack.is(tag);
    }
}
