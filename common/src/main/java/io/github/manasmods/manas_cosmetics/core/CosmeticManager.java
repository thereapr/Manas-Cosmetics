package io.github.manasmods.manas_cosmetics.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.api.CosmeticSlot;
import io.github.manasmods.manas_cosmetics.api.WeaponType;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelParser;
import io.github.manasmods.manas_cosmetics.core.BuiltinPetModels;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Central registry for all loaded cosmetics.
 *
 * On startup (and on /manas_cosmetics reload) it scans:
 *   config/manas_cosmetics/cosmetics/   – one .json sidecar per cosmetic
 *   config/manas_cosmetics/models/      – .bbmodel files referenced by sidecars
 *
 * All loaded data is held in memory; no intermediate files are written.
 */
public final class CosmeticManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("manas_cosmetics");
    private static final CosmeticManager INSTANCE = new CosmeticManager();

    private final Map<String, CosmeticDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, BBModelData> models = new ConcurrentHashMap<>();

    private Path configRoot;

    private CosmeticManager() {}

    public static CosmeticManager get() {
        return INSTANCE;
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    public void init(MinecraftServer server) {
        configRoot = server.getServerDirectory()
            .resolve("config")
            .resolve("manas_cosmetics");
        ensureDirectories();
        BuiltinPetModels.extractIfNeeded(configRoot);
        writeReadme();
        reload();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(configRoot.resolve("cosmetics"));
            // Create a subdirectory under models/ for every cosmetic slot.
            // The weapon slot gets a further subfolder per weapon type so that
            // the generator can deduce both slot and weapon_type from the path.
            for (CosmeticSlot slot : CosmeticSlot.values()) {
                if (slot == CosmeticSlot.WEAPON) {
                    for (WeaponType type : WeaponType.values()) {
                        Files.createDirectories(
                            configRoot.resolve("models").resolve("weapon").resolve(type.getId())
                        );
                    }
                } else {
                    Files.createDirectories(configRoot.resolve("models").resolve(slot.getId()));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create manas_cosmetics config directories", e);
        }
    }

    private void writeReadme() {
        Path readme = configRoot.resolve("README.txt");
        String content = """
            Manas Cosmetics Configuration
            ==============================

            This directory contains cosmetic definition files for Manas Cosmetics.
            Each .json file in the cosmetics/ folder defines a single cosmetic item
            that can be equipped by players.

            ──────────────────────────────────────────────────────────────────────
            Quick Start (Recommended Workflow)
            ──────────────────────────────────────────────────────────────────────

            1. Drop your .bbmodel file into the correct slot subfolder under models/
               (see "Models Directory Structure" below for the folder layout).
            2. Run /manas_cosmetics generate in-game.
               This scans every slot subfolder and creates a .json sidecar in
               cosmetics/ for every .bbmodel that doesn't already have one.
               The command also performs a reload, so cosmetics are live immediately.
            3. (Optional) Open the generated .json in cosmetics/ and tweak the
               scale, offset, rotation, or display_name to taste.
            4. If you edited a .json, run /manas_cosmetics reload to apply changes.

            ──────────────────────────────────────────────────────────────────────
            Models Directory Structure
            ──────────────────────────────────────────────────────────────────────

            Place .bbmodel files in the subfolder that matches the cosmetic slot.
            The slot (and, for weapons, the weapon type) is deduced from the path
            automatically when you run /manas_cosmetics generate.

              models/
                helmet/            ← hat/crown cosmetics
                above_head/        ← floating items above the head
                chestplate/        ← chest cosmetics
                back/              ← wings, capes, backpacks
                front/             ← chest-front cosmetics
                legs/              ← leg cosmetics
                boots/             ← foot cosmetics
                ears/              ← ear cosmetics (attaches to head, both ears in one model)
                orbit/             ← orbiting particles/objects
                pet/               ← companion pet models (auto-scaled to ≤1 block tall)
                aura/              ← (no model needed) code-generated aura rings around the player
                weapon/            ← main-hand weapon overlays (renders over held item)
                  sword/           ←   shown when holding a sword
                  longsword/       ←   shown when holding a longsword
                  katana/          ←   shown when holding a katana
                  kodachi/         ←   shown when holding a kodachi
                  spear/           ←   shown when holding a spear
                  hammer/          ←   shown when holding a hammer
                  axe/             ←   shown when holding an axe
                  scythe/          ←   shown when holding a scythe
                  bow/             ←   shown when holding a bow
                  kunai/           ←   shown when holding a kunai
                  pickaxe/         ←   shown when holding a pickaxe
                  gauntlet/        ←   shown when holding a gauntlet
                  greatsword/      ←   shown when holding a greatsword
                  shield/          ←   shown when holding a shield
                  grimoire/        ←   shown when holding a grimoire
                  magic_staff/     ←   shown when holding a magic staff
                  any/             ←   shown with any weapon
                shield/            ← off-hand shield slot cosmetics
                grimoire/          ← off-hand grimoire slot cosmetics
                magic_staff/       ← main-hand magic staff slot cosmetics

            ──────────────────────────────────────────────────────────────────────
            Cosmetic .json File Structure
            ──────────────────────────────────────────────────────────────────────

            {
              "id":                  "manas_cosmetics:cosmetic_name",
              "display_name":        "Cosmetic Display Name",
              "slot":                "back",
              "weapon_type":         "any",
              "force_equip_allowed": true,
              "model":               "models/back/cosmetic_model.bbmodel",
              "scale":               [1.0, 1.0, 1.0],
              "offset":              [0.0, 0.0, 0.0],
              "rotation":            [180.0, 0.0, 0.0]
            }

            ──────────────────────────────────────────────────────────────────────
            Field Explanations
            ──────────────────────────────────────────────────────────────────────

            Required Fields
            ───────────────
              id               Unique identifier for the cosmetic.
                               Format: mod_id:cosmetic_name
                               Example: "manas_cosmetics:angel_wings"

              display_name     Human-readable name shown in the wardrobe GUI.
                               Example: "Angel Wings"

              slot             Attachment point on the player model where the
                               cosmetic appears. See "Available Slots" below.

              model            Path to the .bbmodel file, relative to the
                               config/manas_cosmetics/ folder.
                               Example: "models/back/angel_wings.bbmodel"

            Optional Fields
            ───────────────
              weapon_type          Restricts cosmetic visibility to players holding
                                   a specific weapon class. Defaults to "any".
                                   See "Weapon Types" below for valid values.

              force_equip_allowed  When true, players can equip this cosmetic even
                                   if they are not holding the required weapon type.
                                   Defaults to true.

              scale                [x, y, z] multiplier for the cosmetic's size.
                                   Defaults to [1.0, 1.0, 1.0] (original size).

              offset               [x, y, z] position adjustment relative to the
                                   attachment point, in BBModel units.
                                   Defaults to [0.0, 0.0, 0.0].

              rotation             [x, y, z] rotation in degrees applied after scale.
                                   Defaults to [180.0, 0.0, 0.0].
                                   The default 180° X rotation corrects for most
                                   Blockbench exports which face downward. Override
                                   only if your model is already oriented correctly.

            ──────────────────────────────────────────────────────────────────────
            Scale Values
            ──────────────────────────────────────────────────────────────────────

            The scale array controls the size of your cosmetic in three dimensions:

              "scale": [scaleX, scaleY, scaleZ]

              scaleX   Width scaling  (left–right)
              scaleY   Height scaling (up–down)
              scaleZ   Depth scaling  (front–back)

            Common Scale Values
            ───────────────────
              [1.0, 1.0, 1.0]   Original size (no scaling)
              [0.5, 0.5, 0.5]   Half size
              [1.5, 1.5, 1.5]   50% larger
              [0.8, 0.8, 0.8]   Slightly smaller (good for wings)
              [1.2, 1.2, 1.2]   Slightly larger

            Scale Tips
            ──────────
              Wings / Capes    Use smaller values (0.6–0.8) to avoid body clipping.
              Hats / Helmets   Use 1.0 or slightly above for a proper fit.
              Accessories      Adjust based on visual reference in-game.
              Asymmetric       Use different values per axis when needed,
                               e.g. [1.0, 0.8, 1.2] stretches depth without
                               changing width or height.

            ──────────────────────────────────────────────────────────────────────
            Offset Values
            ──────────────────────────────────────────────────────────────────────

            The offset array controls the position of your cosmetic relative to
            the attachment point:

              "offset": [offsetX, offsetY, offsetZ]

              offsetX   Left–right  (negative = left,    positive = right)
              offsetY   Up–down     (negative = down,     positive = up)
              offsetZ   Front–back  (negative = forward,  positive = back)

            Common Offset Values by Slot
            ────────────────────────────
              Back slot ("slot": "back")
                [0.0,  0.1, -0.2]   Standard back position
                [0.0,  0.2, -0.15]  Slightly higher and forward
                [0.0,  0.3, -0.1]   Higher on back, less depth

              Head slot ("slot": "helmet")
                [0.0,  0.25,  0.0]  On top of the head
                [0.0,  0.9,   0.0]  Floating just above the head

              Chest slot ("slot": "chestplate")
                [0.0,  0.1,   0.0]  Centred on the chest

              Front slot ("slot": "front")
                [0.0,  0.1,   0.2]  In front of the chest

            Offset Tips
            ───────────
              Start small        Begin with 0.1-unit increments and adjust.
              Test in-game       Check positioning from multiple angles.
              Consider anims     Ensure the offset works with player animations.
              Slot-specific      Each slot has a different base position, so
                                 offsets are not transferable between slots.

            ──────────────────────────────────────────────────────────────────────
            Available Slots
            ──────────────────────────────────────────────────────────────────────

              helmet       On the player's head
              above_head   Floating above the head
              chestplate   On the chest
              back         On the back (wings, capes, backpacks)
              front        In front of the chest
              legs         On the legs
              boots        On the feet
              ears         Attached to the sides of the head (model defines both ears)
              orbit        Orbiting around the player
              pet          Companion entity (auto-scaled to ≤1 block tall)
              aura         Code-generated glowing rings centred on the player (no model file needed)
              weapon       Overlaid on the held weapon / tool
              shield       In the shield (off) hand
              grimoire     In the offhand as a magic book
              magic_staff  In the main hand as a staff

            ──────────────────────────────────────────────────────────────────────
            Weapon Types
            ──────────────────────────────────────────────────────────────────────

              any          No restriction — always visible
              sword        Swords only
              axe          Axes only
              bow          Bows only
              hammer       Hammers only
              katana       Katanas only
              kodachi      Kodachi only
              kunai        Kunai only
              longsword    Longswords only
              magic_staff  Magic staves only
              scythe       Scythes only
              shield       Shields only
              spear        Spears only
              pickaxe      Pickaxes only
              gauntlet     Gauntlets only
              greatsword   Greatswords only

            ──────────────────────────────────────────────────────────────────────
            Example Configurations
            ──────────────────────────────────────────────────────────────────────

            Basic Wings (models/back/angel_wings.bbmodel)
            ───────────────────────────────────────────
            {
              "id":           "manas_cosmetics:angel_wings",
              "display_name": "Angel Wings",
              "slot":         "back",
              "model":        "models/back/angel_wings.bbmodel",
              "scale":        [0.7, 0.7, 0.7],
              "offset":       [0.0, 0.2, -0.15]
            }

            Sword Weapon Overlay (models/weapon/sword/icicle_blade.bbmodel)
            ───────────────────────────────────────────────────────────────
            {
              "id":           "manas_cosmetics:icicle_blade",
              "display_name": "Icicle Blade",
              "slot":         "weapon",
              "weapon_type":  "sword",
              "model":        "models/weapon/sword/icicle_blade.bbmodel",
              "scale":        [1.0, 1.0, 1.0],
              "offset":       [0.0, 0.0, 0.0]
            }

            Floating Hat (models/above_head/wizard_hat.bbmodel)
            ─────────────────────────────────────────────────
            {
              "id":           "manas_cosmetics:wizard_hat",
              "display_name": "Wizard Hat",
              "slot":         "above_head",
              "model":        "models/above_head/wizard_hat.bbmodel",
              "scale":        [1.0, 1.0, 1.0],
              "offset":       [0.0, 0.9, 0.0]
            }

            ──────────────────────────────────────────────────────────────────────
            Troubleshooting
            ──────────────────────────────────────────────────────────────────────

            Cosmetic Not Visible
              - Check that the model path in your .json matches the actual file name.
              - Verify scale isn't too small — try [1.0, 1.0, 1.0] to rule it out.
              - Ensure the chosen slot has an attachment point on the player model.

            Poor Positioning
              - Adjust offset values in small increments (0.05–0.1 at a time).
              - Try different scale values; large cosmetics may appear off-centre.
              - Remember that each slot has a different base origin.

            Clipping Into the Body
              - Reduce scale values.
              - Adjust offset to move the cosmetic away from the body.
              - Try a different attachment slot if clipping persists.

            Red Tint / Wrong Colours
              - Ensure the model has a valid texture assigned in Blockbench.
              - Use PNG format for textures.
              - Verify the model's UV mapping is correct.

            ──────────────────────────────────────────────────────────────────────
            File Organisation
            ──────────────────────────────────────────────────────────────────────

              config/manas_cosmetics/
                cosmetics/              ← .json sidecar files (auto-generated or manual)
                models/
                  <slot>/               ← .bbmodel files for each slot
                  weapon/
                    <weapon_type>/      ← .bbmodel files for each weapon type
                README.txt              ← this file (auto-updated on startup)

            Naming tips:
              - Use descriptive, lowercase file names, e.g. angel_wings.bbmodel
              - The cosmetic "id" field must be unique across all loaded cosmetics.
              - If two models in different slot folders share the same filename, the
                second one will be skipped during generate — use distinct names.

            ──────────────────────────────────────────────────────────────────────
            Commands
            ──────────────────────────────────────────────────────────────────────

              /manas_cosmetics generate   (OP) Scan models/ subfolders and create
                                          .json sidecars for any .bbmodel files that
                                          don't already have one, then auto-reload.

              /manas_cosmetics reload     (OP) Reload all .json files in cosmetics/
                                          without restarting the server.

              /manas_cosmetics list       (OP) List all currently loaded cosmetics.

              /manas_cosmetics give <player> <id>
                                          (OP) Equip a cosmetic on a player.

              /manas_cosmetics wardrobe   Open the wardrobe GUI (any player).
            """;
        try {
            Files.writeString(readme, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not write README.txt", e);
        }
    }

    // ── Reload ─────────────────────────────────────────────────────────────────

    public void reload() {
        definitions.clear();
        models.clear();

        if (configRoot == null) return;

        Path cosmeticsDir = configRoot.resolve("cosmetics");
        try (Stream<Path> files = Files.list(cosmeticsDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                 .forEach(this::loadSidecar);
        } catch (IOException e) {
            LOGGER.error("Failed to list cosmetics directory", e);
        }

        LOGGER.info("[manas_cosmetics] Loaded {} cosmetic(s).", definitions.size());
    }

    private void loadSidecar(Path sidecarPath) {
        try {
            String json = Files.readString(sidecarPath, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            CosmeticDefinition def = CosmeticDefinition.fromJson(obj);

            BBModelData modelData;

            if (def.mobType() != null && !def.mobType().isEmpty()) {
                // Vanilla mob pet: the BBModel is optional.  If one exists we still
                // load it (could be used as a fallback), otherwise use an empty placeholder.
                Path modelPath = configRoot.resolve(def.modelPath());
                if (!def.modelPath().isEmpty() && Files.exists(modelPath)) {
                    String bbmodelJson = Files.readString(modelPath, StandardCharsets.UTF_8);
                    modelData = BBModelParser.parse(bbmodelJson);
                } else {
                    modelData = BBModelData.empty();
                }
            } else {
                // Standard BBModel cosmetic: the model file is required.
                Path modelPath = configRoot.resolve(def.modelPath());
                if (!Files.exists(modelPath)) {
                    LOGGER.warn("[manas_cosmetics] Model file not found for cosmetic '{}': {}", def.id(), modelPath);
                    return;
                }
                String bbmodelJson = Files.readString(modelPath, StandardCharsets.UTF_8);
                modelData = BBModelParser.parse(bbmodelJson);
            }

            definitions.put(def.id(), def);
            models.put(def.id(), modelData);

        } catch (Exception e) {
            LOGGER.error("[manas_cosmetics] Failed to load sidecar: {}", sidecarPath, e);
        }
    }

    // ── Sidecar Generation ─────────────────────────────────────────────────────

    /**
     * Walks every slot subfolder under {@code models/} and creates a {@code .json}
     * sidecar in {@code cosmetics/} for each {@code .bbmodel} file that does not
     * already have one.
     *
     * <p>Expected directory layout:</p>
     * <pre>
     *   models/&lt;slot&gt;/&lt;name&gt;.bbmodel
     *   models/weapon/&lt;weapon_type&gt;/&lt;name&gt;.bbmodel
     * </pre>
     *
     * <p>Generated fields:</p>
     * <ul>
     *   <li>{@code id}           – {@code manas_cosmetics:<name>}</li>
     *   <li>{@code display_name} – title-cased from the filename (underscores → spaces)</li>
     *   <li>{@code slot}         – taken from the first path segment under {@code models/}</li>
     *   <li>{@code weapon_type}  – taken from the second segment when the slot is {@code weapon}</li>
     *   <li>{@code model}        – relative path from {@code config/manas_cosmetics/}</li>
     *   <li>Defaults vary by slot: helmet/above_head use offset&amp;rotation tuned for head attachment;
     *       chestplate/back use chest/back-specific offsets; all others default to {@code offset [0,0,0]}, {@code rotation [180,0,0]}</li>
     * </ul>
     *
     * @return number of new sidecar files written
     */
    public int generateSidecars() {
        if (configRoot == null) return 0;

        Path modelsRoot   = configRoot.resolve("models");
        Path cosmeticsDir = configRoot.resolve("cosmetics");
        int  generated    = 0;

        try (Stream<Path> walk = Files.walk(modelsRoot)) {
            List<Path> bbmodels = walk
                .filter(p -> p.toString().endsWith(".bbmodel"))
                .toList();

            for (Path bbmodelPath : bbmodels) {
                Path relToModels = modelsRoot.relativize(bbmodelPath);
                int  nameCount   = relToModels.getNameCount();

                // Must be at least slot/name.bbmodel (2 segments)
                if (nameCount < 2) {
                    LOGGER.warn("[manas_cosmetics] Skipping model not in a slot subfolder: {}", bbmodelPath);
                    continue;
                }

                String slotId = relToModels.getName(0).toString();

                Optional<CosmeticSlot> slotOpt = CosmeticSlot.fromId(slotId);
                if (slotOpt.isEmpty()) {
                    LOGGER.warn("[manas_cosmetics] Skipping model in unknown slot folder '{}': {}", slotId, bbmodelPath);
                    continue;
                }
                CosmeticSlot slot = slotOpt.get();

                // Weapon slot requires a weapon_type subfolder (3 segments)
                WeaponType weaponType = WeaponType.ANY;
                if (slot == CosmeticSlot.WEAPON) {
                    if (nameCount < 3) {
                        LOGGER.warn("[manas_cosmetics] Weapon model must be inside a weapon-type subfolder, skipping: {}", bbmodelPath);
                        continue;
                    }
                    String weaponTypeId = relToModels.getName(1).toString();
                    weaponType = WeaponType.fromId(weaponTypeId);
                }

                // Derive cosmetic name from the filename (without .bbmodel)
                String fileName = relToModels.getFileName().toString();
                String name     = fileName.substring(0, fileName.length() - ".bbmodel".length());

                // Skip if a sidecar already exists — preserves manual edits.
                // If two models in different slot folders share the same filename,
                // the first one processed wins; staff should use distinct names.
                Path sidecarPath = cosmeticsDir.resolve(name + ".json");
                if (Files.exists(sidecarPath)) {
                    continue;
                }

                // Build the model path relative to configRoot
                String modelRelPath = configRoot.relativize(bbmodelPath).toString().replace('\\', '/');

                String id          = "manas_cosmetics:" + name;
                String displayName = toDisplayName(name);

                String json = buildSidecarJson(id, displayName, slot, weaponType, modelRelPath);
                Files.writeString(sidecarPath, json, StandardCharsets.UTF_8);
                LOGGER.info("[manas_cosmetics] Generated sidecar: {}", sidecarPath.getFileName());
                generated++;
            }
        } catch (IOException e) {
            LOGGER.error("[manas_cosmetics] Failed during sidecar generation", e);
        }

        return generated;
    }

    /** Converts a snake_case filename stem to a title-cased display name. */
    private static String toDisplayName(String name) {
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private static String buildSidecarJson(
        String id, String displayName, CosmeticSlot slot, WeaponType weaponType, String modelPath
    ) {
        String scale, offset, rotation;
        switch (slot) {
            case HELMET -> {
                scale    = "[1.0, 1.0, 1.0]";
                offset   = "[0.0, -3.5, 0.0]";
                rotation = "[180.0, 180.0, 0.0]";
            }
            case ABOVE_HEAD -> {
                scale    = "[1.0, 1.0, 1.0]";
                offset   = "[0.0, -13.0, 0.0]";
                rotation = "[180.0, 180.0, 0.0]";
            }
            case CHESTPLATE -> {
                scale    = "[1.0, 1.0, 1.0]";
                offset   = "[0.0, 3.2, 0.0]";
                rotation = "[180.0, 0.0, 0.0]";
            }
            case BACK -> {
                scale    = "[1.0, 1.0, 1.0]";
                offset   = "[0.0, 17.0, 3.0]";
                rotation = "[180.0, 0.0, 0.0]";
            }
            default -> {
                scale    = "[1.0, 1.0, 1.0]";
                offset   = "[0.0, 0.0, 0.0]";
                rotation = "[180.0, 0.0, 0.0]";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\":                  \"").append(id).append("\",\n");
        sb.append("  \"display_name\":        \"").append(displayName).append("\",\n");
        sb.append("  \"slot\":                \"").append(slot.getId()).append("\",\n");
        if (slot == CosmeticSlot.WEAPON && weaponType != WeaponType.ANY) {
            sb.append("  \"weapon_type\":         \"").append(weaponType.getId()).append("\",\n");
        }
        sb.append("  \"force_equip_allowed\": true,\n");
        sb.append("  \"model\":               \"").append(modelPath).append("\",\n");
        if (slot == CosmeticSlot.PET) {
            // Pets render with their own transform logic; offset/rotation are ignored at runtime.
            sb.append("  \"scale\":               ").append(scale).append("\n");
        } else {
            sb.append("  \"scale\":               ").append(scale).append(",\n");
            sb.append("  \"offset\":              ").append(offset).append(",\n");
            sb.append("  \"rotation\":            ").append(rotation).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    // ── Query ──────────────────────────────────────────────────────────────────

    public Optional<CosmeticDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<BBModelData> getModel(String id) {
        return Optional.ofNullable(models.get(id));
    }

    public Collection<CosmeticDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public boolean exists(String id) {
        return definitions.containsKey(id);
    }
}
