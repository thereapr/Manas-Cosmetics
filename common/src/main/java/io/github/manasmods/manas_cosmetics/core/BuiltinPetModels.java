package io.github.manasmods.manas_cosmetics.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates and extracts simple placeholder .bbmodel files for the five built-in
 * pet cosmetics (pig, chicken, wolf, wither, ender dragon) on first server startup.
 *
 * <p>The models are intentionally minimal — blocky Blockbench geometry with no
 * embedded texture (falls back to a white fallback texture at runtime). The
 * {@link PetCosmeticRenderer} auto-scales each model to stay within 1 block tall.</p>
 *
 * <p>Users can replace the generated .bbmodel files with their own artwork at any
 * time; the extractor checks for file existence and never overwrites.</p>
 */
public final class BuiltinPetModels {

    private static final Logger LOGGER = LoggerFactory.getLogger("manas_cosmetics");

    private BuiltinPetModels() {}

    // ── Public API ─────────────────────────────────────────────────────────────

    public static void extractIfNeeded(Path configRoot) {
        Path petModelDir  = configRoot.resolve("models").resolve("pet");
        Path cosmeticsDir = configRoot.resolve("cosmetics");

        for (PetSpec spec : PETS) {
            Path modelPath   = petModelDir.resolve(spec.id() + ".bbmodel");
            Path sidecarPath = cosmeticsDir.resolve(spec.id() + ".json");

            writeIfAbsent(modelPath,   buildBBModel(spec.id(), spec.cubes()));
            writeIfAbsent(sidecarPath, buildSidecar(spec.id(), spec.displayName()));
        }
    }

    // ── Pet specs ──────────────────────────────────────────────────────────────

    /** Compact cube descriptor: name, from[3], to[3] (all in BBModel units). */
    private record CubeDef(String name, float fx, float fy, float fz,
                           float tx, float ty, float tz) {}

    private record PetSpec(String id, String displayName, List<CubeDef> cubes) {}

    private static final List<PetSpec> PETS = List.of(
        new PetSpec("pet_pig", "Pig", List.of(
            new CubeDef("body",    -6f,  4f, -4f,   6f, 10f,  4f),
            new CubeDef("head",    -4f, 10f, -8f,   4f, 16f, -2f),
            new CubeDef("snout",   -2f, 11f,-10f,   2f, 14f, -7f),
            new CubeDef("leg_fl",  -5f,  0f, -3f,  -2f,  4f, -1f),
            new CubeDef("leg_fr",   2f,  0f, -3f,   5f,  4f, -1f),
            new CubeDef("leg_bl",  -5f,  0f,  1f,  -2f,  4f,  3f),
            new CubeDef("leg_br",   2f,  0f,  1f,   5f,  4f,  3f)
        )),
        new PetSpec("pet_chicken", "Chicken", List.of(
            new CubeDef("body",   -4f,  4f, -3f,   4f, 10f,  3f),
            new CubeDef("head",   -3f, 10f, -5f,   3f, 15f,  0f),
            new CubeDef("beak",   -1f, 12f, -7f,   1f, 14f, -5f),
            new CubeDef("wing_l", -7f,  5f, -2f,  -4f,  9f,  2f),
            new CubeDef("wing_r",  4f,  5f, -2f,   7f,  9f,  2f),
            new CubeDef("leg_l",  -3f,  0f,  0f,  -1f,  4f,  2f),
            new CubeDef("leg_r",   1f,  0f,  0f,   3f,  4f,  2f)
        )),
        // Wolf is ~23 BBM tall — PetCosmeticRenderer auto-scales to 1 block
        new PetSpec("pet_wolf", "Wolf", List.of(
            new CubeDef("body",    -5f,  7f, -4f,   5f, 14f,  5f),
            new CubeDef("head",    -4f, 14f, -8f,   4f, 20f,  0f),
            new CubeDef("ear_l",   -4f, 19f, -7f,  -1f, 23f, -5f),
            new CubeDef("ear_r",    1f, 19f, -7f,   4f, 23f, -5f),
            new CubeDef("snout",   -2f, 14f,-10f,   2f, 17f, -7f),
            new CubeDef("leg_fl",  -4f,  0f, -3f,  -1f,  7f,  0f),
            new CubeDef("leg_fr",   1f,  0f, -3f,   4f,  7f,  0f),
            new CubeDef("leg_bl",  -4f,  0f,  2f,  -1f,  7f,  5f),
            new CubeDef("leg_br",   1f,  0f,  2f,   4f,  7f,  5f),
            new CubeDef("tail",    -2f,  9f,  5f,   2f, 12f, 10f)
        )),
        // Wither is ~24 BBM tall — auto-scaled to 1 block
        new PetSpec("pet_wither", "Wither", List.of(
            new CubeDef("spine",    -2f,  0f, -1f,   2f, 10f,  1f),
            new CubeDef("rib_l",    -5f,  6f, -1f,  -2f,  9f,  1f),
            new CubeDef("rib_r",     2f,  6f, -1f,   5f,  9f,  1f),
            new CubeDef("arm_l",    -8f, 10f, -1f,  -2f, 12f,  1f),
            new CubeDef("arm_r",     2f, 10f, -1f,   8f, 12f,  1f),
            new CubeDef("skull_c",  -3f, 18f, -3f,   3f, 24f,  3f),
            new CubeDef("skull_l", -10f, 15f, -3f,  -4f, 21f,  3f),
            new CubeDef("skull_r",   4f, 15f, -3f,  10f, 21f,  3f)
        )),
        // Ender dragon — ~21 BBM tall, wide wings auto-scaled to 1 block height
        new PetSpec("pet_ender_dragon", "Ender Dragon", List.of(
            new CubeDef("body",     -5f,  6f, -2f,   5f, 13f,  7f),
            new CubeDef("neck",     -3f, 11f, -6f,   3f, 15f,  0f),
            new CubeDef("head",     -4f, 13f,-12f,   4f, 19f, -5f),
            new CubeDef("jaw",      -3f, 11f,-12f,   3f, 13f, -5f),
            new CubeDef("horn_l",   -3f, 18f,-11f,  -1f, 21f, -9f),
            new CubeDef("horn_r",    1f, 18f,-11f,   3f, 21f, -9f),
            new CubeDef("wing_l",  -15f,  8f, -1f,  -5f, 12f,  4f),
            new CubeDef("wing_r",    5f,  8f, -1f,  15f, 12f,  4f),
            new CubeDef("tail1",    -3f,  5f,  7f,   3f, 10f, 12f),
            new CubeDef("tail2",    -2f,  3f, 12f,   2f,  8f, 17f),
            new CubeDef("tail3",    -1f,  2f, 17f,   1f,  6f, 21f)
        ))
    );

    // ── JSON builders ──────────────────────────────────────────────────────────

    private static String buildBBModel(String name, List<CubeDef> cubes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"meta\": {\"format_version\": \"4.10\", \"model_format\": \"free\"},\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"resolution\": {\"width\": 16, \"height\": 16},\n");
        sb.append("  \"textures\": [{\"id\": \"0\", \"name\": \"texture\"}],\n");

        // Elements
        sb.append("  \"elements\": [\n");
        for (int i = 0; i < cubes.size(); i++) {
            CubeDef c  = cubes.get(i);
            String uuid = String.format("%08d-0000-0000-0000-%012d", i, i);
            float px = (c.fx() + c.tx()) / 2f;
            float py = (c.fy() + c.ty()) / 2f;
            float pz = (c.fz() + c.tz()) / 2f;
            sb.append("    {\n");
            sb.append("      \"uuid\": \"").append(uuid).append("\",\n");
            sb.append("      \"name\": \"").append(c.name()).append("\",\n");
            sb.append(String.format(java.util.Locale.ROOT,
                "      \"from\": [%s,%s,%s],\n", c.fx(), c.fy(), c.fz()));
            sb.append(String.format(java.util.Locale.ROOT,
                "      \"to\":   [%s,%s,%s],\n", c.tx(), c.ty(), c.tz()));
            sb.append(String.format(java.util.Locale.ROOT,
                "      \"origin\": [%s,%s,%s],\n", px, py, pz));
            sb.append("      \"rotation\": [0, 0, 0],\n");
            sb.append("      \"faces\": {\n");
            sb.append("        \"north\": {\"uv\": [0,0,16,16], \"texture\": 0},\n");
            sb.append("        \"south\": {\"uv\": [0,0,16,16], \"texture\": 0},\n");
            sb.append("        \"east\":  {\"uv\": [0,0,16,16], \"texture\": 0},\n");
            sb.append("        \"west\":  {\"uv\": [0,0,16,16], \"texture\": 0},\n");
            sb.append("        \"up\":    {\"uv\": [0,0,16,16], \"texture\": 0},\n");
            sb.append("        \"down\":  {\"uv\": [0,0,16,16], \"texture\": 0}\n");
            sb.append("      }\n");
            sb.append("    }");
            if (i < cubes.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Outliner — flat group referencing all cubes
        sb.append("  \"outliner\": [{\n");
        sb.append("    \"name\": \"root\",\n");
        sb.append("    \"origin\": [0, 0, 0],\n");
        sb.append("    \"children\": [");
        for (int i = 0; i < cubes.size(); i++) {
            String uuid = String.format("%08d-0000-0000-0000-%012d", i, i);
            if (i > 0) sb.append(", ");
            sb.append("\"").append(uuid).append("\"");
        }
        sb.append("]\n");
        sb.append("  }],\n");

        sb.append("  \"animations\": []\n");
        sb.append("}");
        return sb.toString();
    }

    private static String buildSidecar(String id, String displayName) {
        return "{\n"
            + "  \"id\":                  \"manas_cosmetics:" + id + "\",\n"
            + "  \"display_name\":        \"" + displayName + "\",\n"
            + "  \"slot\":                \"pet\",\n"
            + "  \"force_equip_allowed\": true,\n"
            + "  \"model\":               \"models/pet/" + id + ".bbmodel\",\n"
            + "  \"scale\":               [1.0, 1.0, 1.0],\n"
            + "  \"offset\":              [0.0, 0.0, 0.0],\n"
            + "  \"rotation\":            [180.0, 0.0, 0.0]\n"
            + "}";
    }

    // ── File helpers ───────────────────────────────────────────────────────────

    private static void writeIfAbsent(Path path, String content) {
        if (Files.exists(path)) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            LOGGER.info("[manas_cosmetics] Extracted builtin pet: {}", path.getFileName());
        } catch (IOException e) {
            LOGGER.error("[manas_cosmetics] Failed to write builtin pet model: {}", path, e);
        }
    }
}
