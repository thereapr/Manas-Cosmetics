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
            Manas Cosmetics – Staff Guide
            ==============================

            How to add a cosmetic:
            1. Drop your .bbmodel file into:  config/manas_cosmetics/models/
            2. Create a .json sidecar in:      config/manas_cosmetics/cosmetics/
            3. Run /manas_cosmetics reload in-game — no server restart needed.

            Sidecar JSON format:
            {
              "id":                  "manas_cosmetics:icicle_wings",
              "display_name":        "Icicle Wings",
              "slot":                "back",
              "weapon_type":         "any",
              "force_equip_allowed": true,
              "model":               "models/icicle_wings.bbmodel",
              "scale":               [1.0, 1.0, 1.0],
              "offset":              [0.0, 0.0, 0.0],
              "rotation":            [180.0, 0.0, 0.0]
            }

            Field descriptions:
              id                   Namespaced ID, e.g. "manas_cosmetics:my_cosmetic"
              display_name         Name shown in the wardrobe GUI
              slot                 Attachment point on the player (see Valid slots below)
              weapon_type          Restrict visibility to a weapon class (default: "any")
              force_equip_allowed  Allow players to bypass weapon_type check (default: false)
              model                Path to the .bbmodel file relative to this config folder
              scale                [x, y, z] multiplicative scale  (default: [1.0, 1.0, 1.0])
              offset               [x, y, z] position offset in BBModel units  (default: [0.0, 0.0, 0.0])
              rotation             [x, y, z] rotation in degrees applied after scale  (default: [180.0, 0.0, 0.0])
                                   The default 180° X rotation is required for most Blockbench exports
                                   which face downward by default. Override only if your model is
                                   already oriented correctly.

            Valid slots: helmet, above_head, chestplate, back, front, legs, boots,
                         orbit, pet, weapon, shield, grimoire, magic_staff

            Valid weapon_type values: sword, longsword, katana, kodachi, spear,
                         hammer, axe, scythe, bow, kunai, shield, grimoire,
                         magic_staff, any
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
