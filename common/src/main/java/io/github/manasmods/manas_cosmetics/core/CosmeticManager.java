package io.github.manasmods.manas_cosmetics.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelParser;
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
        writeReadme();
        reload();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(configRoot.resolve("cosmetics"));
            Files.createDirectories(configRoot.resolve("models"));
        } catch (IOException e) {
            LOGGER.error("Failed to create manas_cosmetics config directories", e);
        }
    }

    private void writeReadme() {
        Path readme = configRoot.resolve("README.txt");
        if (Files.exists(readme)) return;
        String content = """
            Manas Cosmetics Configuration
            ==============================

            This directory contains cosmetic definition files for Manas Cosmetics.
            Each .json file in the cosmetics/ folder defines a single cosmetic item
            that can be equipped by players.

            How to add a cosmetic:
            1. Drop your .bbmodel file into:  config/manas_cosmetics/models/
            2. Create a .json file in:        config/manas_cosmetics/cosmetics/
            3. Run /manas_cosmetics reload in-game — no server restart needed.

            ──────────────────────────────────────────────────────────────────────
            Cosmetic File Structure
            ──────────────────────────────────────────────────────────────────────

            {
              "id":                  "manas_cosmetics:cosmetic_name",
              "display_name":        "Cosmetic Display Name",
              "slot":                "back",
              "weapon_type":         "any",
              "force_equip_allowed": true,
              "model":               "models/cosmetic_model.bbmodel",
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
                               Example: "models/angel_wings.bbmodel"

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
              orbit        Orbiting around the player
              pet          Displayed as a pet entity
              weapon       In the main weapon hand
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

            ──────────────────────────────────────────────────────────────────────
            Example Configurations
            ──────────────────────────────────────────────────────────────────────

            Basic Wings
            ───────────
            {
              "id":           "manas_cosmetics:angel_wings",
              "display_name": "Angel Wings",
              "slot":         "back",
              "weapon_type":  "any",
              "model":        "models/angel_wings.bbmodel",
              "scale":        [0.7, 0.7, 0.7],
              "offset":       [0.0, 0.2, -0.15]
            }

            Weapon-restricted Accessory
            ───────────────────────────
            {
              "id":           "manas_cosmetics:magic_aura",
              "display_name": "Magic Aura",
              "slot":         "chestplate",
              "weapon_type":  "magic_staff",
              "model":        "models/magic_aura.bbmodel",
              "scale":        [0.5, 0.5, 0.5],
              "offset":       [0.0, 0.1, 0.0]
            }

            Floating Hat
            ────────────
            {
              "id":           "manas_cosmetics:wizard_hat",
              "display_name": "Wizard Hat",
              "slot":         "above_head",
              "weapon_type":  "any",
              "model":        "models/wizard_hat.bbmodel",
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
                cosmetics/              ← place your .json definition files here
                models/                 ← place your .bbmodel files here
                README.txt              ← this file

            Naming tips:
              - Use descriptive, lowercase file names, e.g. angel_wings.json
              - The cosmetic "id" field must be unique across all loaded cosmetics.
              - Matching the file name to the id suffix keeps things organised.

            ──────────────────────────────────────────────────────────────────────
            Reload Command
            ──────────────────────────────────────────────────────────────────────

            Use /manas_cosmetics reload to reload all cosmetic configurations
            without restarting the server. Any .json files added, edited, or
            removed since the last reload will be picked up immediately.
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

            // Load the referenced .bbmodel
            Path modelPath = configRoot.resolve(def.modelPath());
            if (!Files.exists(modelPath)) {
                LOGGER.warn("[manas_cosmetics] Model file not found for cosmetic '{}': {}", def.id(), modelPath);
                return;
            }

            String bbmodelJson = Files.readString(modelPath, StandardCharsets.UTF_8);
            BBModelData modelData = BBModelParser.parse(bbmodelJson);

            definitions.put(def.id(), def);
            models.put(def.id(), modelData);

        } catch (Exception e) {
            LOGGER.error("[manas_cosmetics] Failed to load sidecar: {}", sidecarPath, e);
        }
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
