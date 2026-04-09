package io.github.manasmods.manas_cosmetics.api;

import java.util.Arrays;

public enum WeaponType {
    SWORD("sword"),
    LONGSWORD("longsword"),
    KATANA("katana"),
    KODACHI("kodachi"),
    SPEAR("spear"),
    HAMMER("hammer"),
    AXE("axe"),
    SCYTHE("scythe"),
    BOW("bow"),
    KUNAI("kunai"),
    SHIELD("shield"),
    GRIMOIRE("grimoire"),
    MAGIC_STAFF("magic_staff"),
    ANY("any");

    private final String id;

    WeaponType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static WeaponType fromId(String id) {
        return Arrays.stream(values())
            .filter(t -> t.id.equals(id))
            .findFirst()
            .orElse(ANY);
    }
}
