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
 * Procedurally generates and renders an anime-style "battle aura" around the player:
 * upward-streaming flame tongues that emerge from the ground, taper with height,
 * sway sideways, and pulse in/out independently — think Dragon Ball ki aura.
 *
 * <p>All geometry is generated in code each frame; no model files, no textures
 * (a 1×1 white texture is used so vertex RGBA fully controls the tint). Two passes
 * are drawn:
 * <ul>
 *   <li>A set of tall primary flame tongues arranged in a circle around the body.</li>
 *   <li>A smaller/shorter set of inner flickers with offset timing to fill gaps and
 *       keep the aura feeling chaotic from every angle.</li>
 * </ul>
 *
 * <p>Colour is caller-supplied so the same renderer serves every aura colour variant.</p>
 */
public final class AuraRenderer {

    private AuraRenderer() {}

    // ── Flame tongue geometry ──────────────────────────────────────────────────

    /** Primary flame count arranged around the body. Higher = denser. */
    private static final int PRIMARY_COUNT = 14;

    /** Vertical subdivisions per primary flame tongue — more = smoother sway curve. */
    private static final int PRIMARY_SEGMENTS = 8;

    /** Radius of the primary flame ring, in blocks. */
    private static final float PRIMARY_RADIUS = 0.48f;

    /** Maximum height of a primary flame tongue, in blocks. */
    private static final float PRIMARY_HEIGHT = 2.6f;

    /** Width of the tongue at its base (tapers to 0 at the tip). */
    private static final float PRIMARY_BASE_WIDTH = 0.26f;

    /** Inner flicker count (half as many, offset phase, shorter). */
    private static final int INNER_COUNT = 8;
    private static final int INNER_SEGMENTS = 6;
    private static final float INNER_RADIUS = 0.30f;
    private static final float INNER_HEIGHT = 1.4f;
    private static final float INNER_BASE_WIDTH = 0.18f;

    /**
     * Y-coordinate of the flame base in the render-layer pose space. The layer
     * applies {@code scale(-1,-1,1)} before calling render layers, so <strong>+Y
     * points toward the feet</strong> in this space. The base therefore sits at a
     * small positive Y (just below the feet) and each flame grows toward <em>negative</em>
     * Y, sweeping up past the head as height increases.
     */
    private static final float BASE_Y = 0.95f;

    // ── Default colour (deep blue-violet) ──────────────────────────────────────

    private static final int DEFAULT_COL_R = 140;
    private static final int DEFAULT_COL_G =  80;
    private static final int DEFAULT_COL_B = 255;

    private static ResourceLocation whiteTexture;

    // ── Public render entry points ─────────────────────────────────────────────

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
        // entityTranslucent: translucent blending, no back-face culling. We want no cull
        // so a single outward-facing quad is visible whether the camera is in front of or
        // behind the flame.
        VertexConsumer buf = bufferSource.getBuffer(RenderType.entityTranslucent(tex));
        PoseStack.Pose entry = poseStack.last();
        Matrix4f pose = entry.pose();

        // Primary flame tongues
        for (int i = 0; i < PRIMARY_COUNT; i++) {
            renderFlameTongue(buf, pose, entry, packedLight, animTime,
                    i, PRIMARY_COUNT, PRIMARY_SEGMENTS,
                    PRIMARY_RADIUS, PRIMARY_HEIGHT, PRIMARY_BASE_WIDTH,
                    1.0f, colR, colG, colB);
        }
        // Inner flickers — phase-offset timing so they pulse against the primary ring.
        for (int i = 0; i < INNER_COUNT; i++) {
            renderFlameTongue(buf, pose, entry, packedLight, animTime + 0.37f,
                    i, INNER_COUNT, INNER_SEGMENTS,
                    INNER_RADIUS, INNER_HEIGHT, INNER_BASE_WIDTH,
                    0.85f, colR, colG, colB);
        }
    }

    // ── Flame tongue geometry generation ───────────────────────────────────────

    /**
     * Emits a single flame tongue: a vertical ribbon that starts wide at the base,
     * tapers to a point at the tip, sways side-to-side, and pulses in height.
     * Outward-facing (normal = radial from player centre); relies on no-cull
     * rendering to show both sides.
     */
    private static void renderFlameTongue(VertexConsumer buf,
                                          Matrix4f pose,
                                          PoseStack.Pose entry,
                                          int light,
                                          float animTime,
                                          int index, int total, int segments,
                                          float radius, float height, float baseWidth,
                                          float alphaScale,
                                          int colR, int colG, int colB) {
        float phase = (index / (float) total) * (float) (Math.PI * 2.0);

        // Per-flame pulse: height grows and shrinks on its own cycle.
        float pulse = (float) Math.sin(animTime * 3.5 + index * 1.73);
        float heightMult = 0.65f + 0.35f * pulse;          // 0.30 .. 1.00
        float flameHeight = height * heightMult;

        // Sway magnitude: side-to-side lateral wiggle of the tip.
        float swayAmp = (float) Math.sin(animTime * 4.3 + index * 0.97) * 0.22f;

        // Per-flame alpha jitter to keep things boiling — subtle twinkle.
        float flicker = 0.85f + 0.15f * (float) Math.sin(animTime * 7.1 + index * 2.31);

        for (int j = 0; j < segments; j++) {
            float t0 = j / (float) segments;
            float t1 = (j + 1) / (float) segments;

            // -Y in post-scale render space points upward (toward the head), so
            // subtract to make the flame climb.
            float y0 = BASE_Y - flameHeight * t0;
            float y1 = BASE_Y - flameHeight * t1;

            // Flame profile: wide at base, tapering to a point (quartic falloff).
            float w0 = baseWidth * (float) Math.pow(1.0f - t0, 0.9f);
            float w1 = baseWidth * (float) Math.pow(1.0f - t1, 0.9f);

            // Sway: higher segments sway more (quadratic), so the tip whips while
            // the root stays anchored around the feet.
            float swayOff0 = swayAmp * t0 * t0;
            float swayOff1 = swayAmp * t1 * t1;

            float ang0 = phase + swayOff0;
            float ang1 = phase + swayOff1;

            // Centre of the ribbon at this height — slight outward pull with height
            // gives the flame a soft cone silhouette rather than a strict cylinder.
            float r0 = radius * (1.0f + 0.08f * t0);
            float r1 = radius * (1.0f + 0.08f * t1);

            float cx0 = (float) Math.cos(ang0) * r0;
            float cz0 = (float) Math.sin(ang0) * r0;
            float cx1 = (float) Math.cos(ang1) * r1;
            float cz1 = (float) Math.sin(ang1) * r1;

            // Tangent (horizontal, perpendicular to the outward radial) — the ribbon
            // extends ±w along this direction so the flat face points outward.
            float tx0 = -(float) Math.sin(ang0);
            float tz0 =  (float) Math.cos(ang0);
            float tx1 = -(float) Math.sin(ang1);
            float tz1 =  (float) Math.cos(ang1);

            // Alpha: strong hot core at the base, gentle fade to transparent at the tip.
            int alpha0 = clamp255(Math.round(alphaScale * flicker * (1.0f - t0 * 0.95f) * 230f));
            int alpha1 = clamp255(Math.round(alphaScale * flicker * (1.0f - t1 * 0.95f) * 230f));

            // Outward-facing normal (constant for the whole tongue).
            float nx = (float) Math.cos(phase);
            float nz = (float) Math.sin(phase);

            float blx = cx0 - tx0 * w0, blz = cz0 - tz0 * w0;
            float brx = cx0 + tx0 * w0, brz = cz0 + tz0 * w0;
            float trx = cx1 + tx1 * w1, trz = cz1 + tz1 * w1;
            float tlx = cx1 - tx1 * w1, tlz = cz1 - tz1 * w1;

            emitQuad(buf, pose, entry, light,
                    blx, y0, blz, alpha0, 0f, 1f,
                    brx, y0, brz, alpha0, 1f, 1f,
                    trx, y1, trz, alpha1, 1f, 0f,
                    tlx, y1, tlz, alpha1, 0f, 0f,
                    colR, colG, colB,
                    nx, 0f, nz);
        }
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static void emitQuad(VertexConsumer buf, Matrix4f pose, PoseStack.Pose entry,
                                 int light,
                                 float x0, float y0, float z0, int a0, float u0, float v0,
                                 float x1, float y1, float z1, int a1, float u1, float v1,
                                 float x2, float y2, float z2, int a2, float u2, float v2,
                                 float x3, float y3, float z3, int a3, float u3, float v3,
                                 int colR, int colG, int colB,
                                 float nx, float ny, float nz) {
        buf.addVertex(pose, x0, y0, z0).setColor(colR, colG, colB, a0).setUv(u0, v0)
           .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, nx, ny, nz);
        buf.addVertex(pose, x1, y1, z1).setColor(colR, colG, colB, a1).setUv(u1, v1)
           .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, nx, ny, nz);
        buf.addVertex(pose, x2, y2, z2).setColor(colR, colG, colB, a2).setUv(u2, v2)
           .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, nx, ny, nz);
        buf.addVertex(pose, x3, y3, z3).setColor(colR, colG, colB, a3).setUv(u3, v3)
           .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry, nx, ny, nz);
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
