package io.github.manasmods.manas_cosmetics.api;

import com.google.gson.JsonObject;

/**
 * Represents a single cosmetic item, parsed from a sidecar .json file.
 * The visual data (geometry, texture, animations) comes from the referenced .bbmodel file.
 */
public record CosmeticDefinition(
    String id,
    String displayName,
    CosmeticSlot slot,
    WeaponType weaponType,
    boolean forceEquipAllowed,
    String modelPath,
    float[] scale,
    float[] offset,
    float[] rotation
) {
    public static CosmeticDefinition fromJson(JsonObject json) {
        String id = json.get("id").getAsString();
        String displayName = json.get("display_name").getAsString();
        CosmeticSlot slot = CosmeticSlot.fromId(json.get("slot").getAsString())
            .orElseThrow(() -> new IllegalArgumentException("Unknown slot: " + json.get("slot").getAsString()));
        WeaponType weaponType = json.has("weapon_type")
            ? WeaponType.fromId(json.get("weapon_type").getAsString())
            : WeaponType.ANY;
        boolean forceEquipAllowed = json.has("force_equip_allowed") && json.get("force_equip_allowed").getAsBoolean();
        // model is optional for slots that use code-generated rendering (e.g. AURA)
        String modelPath = json.has("model") ? json.get("model").getAsString() : "";

        float[] scale = {1f, 1f, 1f};
        if (json.has("scale")) {
            var arr = json.getAsJsonArray("scale");
            scale = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }

        float[] offset = {0f, 0f, 0f};
        if (json.has("offset")) {
            var arr = json.getAsJsonArray("offset");
            offset = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }

        // Default X=180 so models face the correct direction out-of-the-box
        float[] rotation = {180f, 0f, 0f};
        if (json.has("rotation")) {
            var arr = json.getAsJsonArray("rotation");
            rotation = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }

        return new CosmeticDefinition(id, displayName, slot, weaponType, forceEquipAllowed, modelPath, scale, offset, rotation);
    }

    /** Namespace portion of the id, e.g. "manas_cosmetics" from "manas_cosmetics:icicle_wings". */
    public String namespace() {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : "manas_cosmetics";
    }

    /** Path portion of the id, e.g. "icicle_wings" from "manas_cosmetics:icicle_wings". */
    public String path() {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
