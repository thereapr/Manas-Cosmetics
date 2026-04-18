package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Procedurally generates and renders a player-centred aura effect.
 *
 * The aura consists of several thin horizontal ribbon rings stacked along the
 * player's body at different heights. Each ring is an N-sided polygon with a
 * small vertical thickness so it remains visible from all camera angles.
 * Adjacent rings counter-rotate to create continuous, layered motion.
 *
 * No external model files are needed — all geometry is generated entirely in
 * code at render time. Color and density will be made configurable per-cosmetic
 * in a later pass; for now a default deep blue-violet palette is used.
 *
 * Geometry overview (per ring):
 *   - N outer-wall quads forming a low cylindrical band (visible from the side)
 *   - N top-face quads and N bottom-face quads closing the annulus
 *   Each quad uses vertex RGBA so the white 1×1 texture acts as a plain multiplier,
 *   keeping colour control entirely in code.
 */
public final class AuraRenderer {

    private AuraRenderer() {}

    // ── Geometry constants ─────────────────────────────────────────────────────

    /** Number of polygon segments per ring. Higher = smoother circle. */
    private static final int SEGMENTS = 16;

    /** Inner radius of the ring band in blocks. */
    private static final float INNER_R = 0.38f;

    /** Outer radius of the ring band in blocks. */
    private static final float OUTER_R = 0.50f;

    /**
     * Vertical height of each ribbon band in blocks.
     * Keeps the ring visible from side-on camera angles.
     */
    private static final float THICKNESS = 0.045f;

    // ── Ring layer table ───────────────────────────────────────────────────────
    // Each row: { yCenter, rotSpeedDeg/sec, alpha [0–1], radiusScale }
    // Alternating rotation direction gives a dynamic layered effect.

    private static final float[][] RING_LAYERS = {
        { -0.62f,  40.0f, 0.90f, 0.90f },
        { -0.32f, -50.0f, 0.88f, 1.00f },
        {  0.02f,  60.0f, 0.85f, 1.08f },
        {  0.40f, -55.0f, 0.80f, 1.10f },
        {  0.78f,  45.0f, 0.70f, 1.05f },
        {  1.10f, -38.0f, 0.55f, 0.97f },
        {  1.38f,  32.0f, 0.38f, 0.85f },
    };

    // ── Default aura colour (deep blue-violet) ─────────────────────────────────

    private static final int DEFAULT_COL_R = 140;
    private static final int DEFAULT_COL_G =  80;
    private static final int DEFAULT_COL_B = 255;

    // ── Shared 1×1 white texture ───────────────────────────────────────────────

    private static ResourceLocation whiteTexture;

    // ── Public render entry point ──────────────────────────────────────────────

    /**
     * Renders the aura at the current pose-stack origin (player body root).
     *
     * @param poseStack    Active pose stack.
     * @param bufferSource Active buffer source.
     * @param packedLight  Combined sky+block light for the player's position.
     * @param animTime     Elapsed time in <em>seconds</em> (= {@code ageInTicks / 20f}).
     */
    public static void render(PoseStack poseStack,
                              MultiBufferSource bufferSource,
                              int packedLight,
                              float animTime) {
        render(poseStack, bufferSource, packedLight, animTime,
               DEFAULT_COL_R, DEFAULT_COL_G, DEFAULT_COL_B);
    }

    /**
     * Renders the aura with a caller-supplied RGB tint (each channel 0-255).
     * Used by {@link CosmeticLayer} to honour the {@code aura_color} field
     * in the equipped aura cosmetic's definition.
     */
    public static void render(PoseStack poseStack,
                              MultiBufferSource bufferSource,
                              int packedLight,
                              float animTime,
                              int colR, int colG, int colB) {

        ResourceLocation tex = ensureWhiteTexture();
        VertexConsumer buf = bufferSource.getBuffer(RenderType.entityTranslucentCull(tex));
        PoseStack.Pose entry = poseStack.last();
        Matrix4f pose = entry.pose();

        for (float[] layer : RING_LAYERS) {
            float yCenter  = layer[0];
            float speedDeg = layer[1]; // degrees per second
            float alpha    = layer[2];
            float rScale   = layer[3];

            float angleDeg  = (animTime * speedDeg) % 360.0f;
            float ir        = INNER_R * rScale;
            float or        = OUTER_R * rScale;
            float halfThick = THICKNESS * 0.5f;

            renderRingLayer(buf, pose, entry, packedLight,
                            yCenter, angleDeg, ir, or, halfThick, alpha,
                            colR, colG, colB);
        }
    }

    // ── Ring geometry generation ───────────────────────────────────────────────

    private static void renderRingLayer(VertexConsumer buf,
                                        Matrix4f pose,
                                        PoseStack.Pose entry,
                                        int light,
                                        float yCenter,
                                        float angleDeg,
                                        float innerR,
                                        float outerR,
                                        float halfThick,
                                        float alpha,
                                        int colR, int colG, int colB) {
        int a    = Math.round(alpha * 255);
        float yTop = yCenter + halfThick;
        float yBot = yCenter - halfThick;

        float segStep = (float) (2.0 * Math.PI / SEGMENTS);
        float baseRad = (float) Math.toRadians(angleDeg);

        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = baseRad + i * segStep;
            float a1 = a0 + segStep;

            float sin0 = (float) Math.sin(a0), cos0 = (float) Math.cos(a0);
            float sin1 = (float) Math.sin(a1), cos1 = (float) Math.cos(a1);

            // Vertex positions at inner and outer radii for both segment edges
            float ix0 = innerR * sin0, iz0 = innerR * cos0;
            float ix1 = innerR * sin1, iz1 = innerR * cos1;
            float ox0 = outerR * sin0, oz0 = outerR * cos0;
            float ox1 = outerR * sin1, oz1 = outerR * cos1;

            // Top face (+Y normal) — visible when camera is above the ring
            quad(buf, pose, entry, light, a, colR, colG, colB,
                 ix0, yTop, iz0,
                 ox0, yTop, oz0,
                 ox1, yTop, oz1,
                 ix1, yTop, iz1,
                 0f, 1f, 0f);

            // Bottom face (-Y normal) — visible when camera is below the ring
            quad(buf, pose, entry, light, a, colR, colG, colB,
                 ix1, yBot, iz1,
                 ox1, yBot, oz1,
                 ox0, yBot, oz0,
                 ix0, yBot, iz0,
                 0f, -1f, 0f);

            // Outer wall — faces radially outward, visible from the side
            float midA = a0 + segStep * 0.5f;
            float nx   = (float) Math.sin(midA);
            float nz   = (float) Math.cos(midA);
            quad(buf, pose, entry, light, a, colR, colG, colB,
                 ox0, yBot, oz0,
                 ox1, yBot, oz1,
                 ox1, yTop, oz1,
                 ox0, yTop, oz0,
                 nx, 0f, nz);
        }
    }

    /** Emits a single quad (4 vertices in order: top-left, top-right, bottom-right, bottom-left). */
    private static void quad(VertexConsumer buf,
                             Matrix4f pose,
                             PoseStack.Pose entry,
                             int light,
                             int alpha,
                             int colR, int colG, int colB,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        buf.addVertex(pose, x0, y0, z0)
           .setColor(colR, colG, colB, alpha)
           .setUv(0f, 0f)
           .setOverlay(OverlayTexture.NO_OVERLAY)
           .setLight(light)
           .setNormal(entry, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1)
           .setColor(colR, colG, colB, alpha)
           .setUv(1f, 0f)
           .setOverlay(OverlayTexture.NO_OVERLAY)
           .setLight(light)
           .setNormal(entry, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2)
           .setColor(colR, colG, colB, alpha)
           .setUv(1f, 1f)
           .setOverlay(OverlayTexture.NO_OVERLAY)
           .setLight(light)
           .setNormal(entry, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3)
           .setColor(colR, colG, colB, alpha)
           .setUv(0f, 1f)
           .setOverlay(OverlayTexture.NO_OVERLAY)
           .setLight(light)
           .setNormal(entry, nx, ny, nz);
    }

    // ── White texture ──────────────────────────────────────────────────────────

    /**
     * Lazily registers a 1×1 opaque-white {@link DynamicTexture}.
     * Vertex RGBA values then fully control the rendered colour.
     */
    private static ResourceLocation ensureWhiteTexture() {
        if (whiteTexture == null) {
            whiteTexture = ResourceLocation.fromNamespaceAndPath(
                    "manas_cosmetics", "dynamic/aura_white_1x1");
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                NativeImage img = new NativeImage(1, 1, false);
                img.setPixelRGBA(0, 0, 0xFFFFFFFF);
                mc.getTextureManager().register(whiteTexture, new DynamicTexture(img));
            });
        }
        return whiteTexture;
    }
}
