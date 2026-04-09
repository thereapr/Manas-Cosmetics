package io.github.manasmods.manas_cosmetics.api;

import java.util.Arrays;
import java.util.Optional;

public enum CosmeticSlot {
    HELMET("helmet"),
    ABOVE_HEAD("above_head"),
    CHESTPLATE("chestplate"),
    BACK("back"),
    FRONT("front"),
    LEGS("legs"),
    BOOTS("boots"),
    ORBIT("orbit"),
    PET("pet"),
    WEAPON("weapon"),
    SHIELD("shield"),
    GRIMOIRE("grimoire"),
    MAGIC_STAFF("magic_staff");

    private final String id;

    CosmeticSlot(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isWeaponSlot() {
        return this == WEAPON || this == SHIELD || this == GRIMOIRE || this == MAGIC_STAFF;
    }

    public boolean isSpecialSlot() {
        return this == ORBIT || this == PET;
    }

    public static Optional<CosmeticSlot> fromId(String id) {
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }
}
