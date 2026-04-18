package io.github.manasmods.manas_cosmetics.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates sidecar .json files for the five built-in aura cosmetics on first
 * server startup. Auras are procedurally rendered by
 * {@link io.github.manasmods.manas_cosmetics.client.renderer.AuraRenderer}, so
 * no .bbmodel file is needed — each sidecar only carries an id, display name,
 * slot, and {@code aura_color}.
 *
 * <p>Existing sidecars are never overwritten so user edits are preserved.</p>
 */
public final class BuiltinAuras {

    private static final Logger LOGGER = LoggerFactory.getLogger("manas_cosmetics");

    private BuiltinAuras() {}

    private record AuraSpec(String id, String displayName, int r, int g, int b) {}

    private static final List<AuraSpec> AURAS = List.of(
        new AuraSpec("aura_azure",   "Azure Aura",    90, 170, 255),
        new AuraSpec("aura_crimson", "Crimson Aura", 255,  70,  80),
        new AuraSpec("aura_emerald", "Emerald Aura",  80, 220, 120),
        new AuraSpec("aura_amber",   "Amber Aura",   255, 190,  60),
        new AuraSpec("aura_violet",  "Violet Aura",  185,  95, 255)
    );

    public static void extractIfNeeded(Path configRoot) {
        Path cosmeticsDir = configRoot.resolve("cosmetics");
        for (AuraSpec spec : AURAS) {
            Path sidecarPath = cosmeticsDir.resolve(spec.id() + ".json");
            writeIfAbsent(sidecarPath, buildSidecar(spec));
        }
    }

    private static String buildSidecar(AuraSpec spec) {
        return "{\n"
            + "  \"id\":                  \"manas_cosmetics:" + spec.id() + "\",\n"
            + "  \"display_name\":        \"" + spec.displayName() + "\",\n"
            + "  \"slot\":                \"aura\",\n"
            + "  \"force_equip_allowed\": true,\n"
            + "  \"aura_color\":          [" + spec.r() + ", " + spec.g() + ", " + spec.b() + "]\n"
            + "}";
    }

    private static void writeIfAbsent(Path path, String content) {
        if (Files.exists(path)) return;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
            LOGGER.info("[manas_cosmetics] Extracted builtin aura: {}", path.getFileName());
        } catch (IOException e) {
            LOGGER.error("[manas_cosmetics] Failed to write builtin aura sidecar: {}", path, e);
        }
    }
}
