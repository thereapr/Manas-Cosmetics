package io.github.manasmods.manas_cosmetics.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;

/**
 * Software renderer for BBModel geometry with animation.
 *
 * Draws each bone recursively, applying keyframe-interpolated transforms.
 * Faces are tessellated as quads and submitted to Minecraft's vertex buffer.
 *
 * Coordinate conventions match Blockbench's Java Edition export:
 *  - 1 block = 16 BBModel units
 *  - Y-up, right-handed
 */
public final class BBModelRenderer {

    private static final float UNITS_TO_BLOCKS = 1f / 16f;

    private BBModelRenderer() {}

    public static void render(PoseStack poseStack,
                              MultiBufferSource bufferSource,
                              int packedLight,
                              BBModelData model,
                              ResourceLocation texture,
                              float animTime) {

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));

        for (BBModelData.Bone bone : model.bones()) {
            renderBone(poseStack, consumer, packedLight, model, bone, animTime);
        }
    }

    private static void renderBone(PoseStack ps,
                                   VertexConsumer consumer,
                                   int light,
                                   BBModelData model,
                                   BBModelData.Bone bone,
                                   float animTime) {
        ps.pushPose();

        // Pivot translation
        float px = bone.pivot()[0] * UNITS_TO_BLOCKS;
        float py = bone.pivot()[1] * UNITS_TO_BLOCKS;
        float pz = bone.pivot()[2] * UNITS_TO_BLOCKS;
        ps.translate(px, py, pz);

        // Animated rotation
        float[] rot = interpolateRotation(model, bone.name(), animTime, bone.rotation());
        if (rot[0] != 0) ps.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(rot[0])));
        if (rot[1] != 0) ps.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(-rot[1])));
        if (rot[2] != 0) ps.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(rot[2])));

        // Animated position offset
        float[] pos = interpolatePosition(model, bone.name(), animTime);
        if (pos[0] != 0 || pos[1] != 0 || pos[2] != 0) {
            ps.translate(pos[0] * UNITS_TO_BLOCKS, pos[1] * UNITS_TO_BLOCKS, pos[2] * UNITS_TO_BLOCKS);
        }

        ps.translate(-px, -py, -pz);

        // Render cubes in this bone
        for (BBModelData.Cube cube : bone.cubes()) {
            renderCube(ps, consumer, light, cube, model.textureWidth(), model.textureHeight());
        }

        // Recurse into children
        for (BBModelData.Bone child : bone.children()) {
            renderBone(ps, consumer, light, model, child, animTime);
        }

        ps.popPose();
    }

    private static void renderCube(PoseStack ps,
                                   VertexConsumer consumer,
                                   int light,
                                   BBModelData.Cube cube,
                                   int texW, int texH) {
        ps.pushPose();

        // Cube pivot & rotation
        float px = cube.pivot()[0] * UNITS_TO_BLOCKS;
        float py = cube.pivot()[1] * UNITS_TO_BLOCKS;
        float pz = cube.pivot()[2] * UNITS_TO_BLOCKS;
        ps.translate(px, py, pz);

        float[] rot = cube.rotation();
        if (rot[0] != 0) ps.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(rot[0])));
        if (rot[1] != 0) ps.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(-rot[1])));
        if (rot[2] != 0) ps.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(rot[2])));
        ps.translate(-px, -py, -pz);

        float x0 = cube.from()[0] * UNITS_TO_BLOCKS;
        float y0 = cube.from()[1] * UNITS_TO_BLOCKS;
        float z0 = cube.from()[2] * UNITS_TO_BLOCKS;
        float x1 = cube.to()[0]   * UNITS_TO_BLOCKS;
        float y1 = cube.to()[1]   * UNITS_TO_BLOCKS;
        float z1 = cube.to()[2]   * UNITS_TO_BLOCKS;

        
        for (Map.Entry<String, BBModelData.Face> entry : cube.faces().entrySet()) {
            BBModelData.Face face = entry.getValue();
            float u0 = face.uv()[0] / texW;
            float v0 = face.uv()[1] / texH;
            float u1 = face.uv()[2] / texW;
            float v1 = face.uv()[3] / texH;

            switch (entry.getKey()) {
                case "north" -> quad(consumer, ps, light,
                    x1,y1,z0,  x0,y1,z0,  x0,y0,z0,  x1,y0,z0,  u0,v0,u1,v1,  0,0,-1);
                case "south" -> quad(consumer, ps, light,
                    x0,y1,z1,  x1,y1,z1,  x1,y0,z1,  x0,y0,z1,  u0,v0,u1,v1,  0,0,1);
                case "west"  -> quad(consumer, ps, light,
                    x0,y1,z0,  x0,y1,z1,  x0,y0,z1,  x0,y0,z0,  u0,v0,u1,v1, -1,0,0);
                case "east"  -> quad(consumer, ps, light,
                    x1,y1,z1,  x1,y1,z0,  x1,y0,z0,  x1,y0,z1,  u0,v0,u1,v1,  1,0,0);
                case "up"    -> quad(consumer, ps, light,
                    x0,y1,z0,  x1,y1,z0,  x1,y1,z1,  x0,y1,z1,  u0,v0,u1,v1,  0,1,0);
                case "down"  -> quad(consumer, ps, light,
                    x0,y0,z1,  x1,y0,z1,  x1,y0,z0,  x0,y0,z0,  u0,v0,u1,v1,  0,-1,0);
            }
        }

        ps.popPose();
    }

    private static void quad(VertexConsumer buf, PoseStack poseStack, int light,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float u0, float v0, float u1, float v1,
                              float nx, float ny, float nz) {
        // Vertex order: position → color → uv0 (texture) → uv1 (overlay) → uv2 (light) → normal
        // setOverlay(NO_OVERLAY) is required; without it the overlay defaults to 0 (the hurt/red
        // position in the overlay texture), producing a red tint over the whole cosmetic.
        final PoseStack.Pose entry = poseStack.last();
        final Matrix4f pose = entry.pose();
        buf.addVertex(pose, x0, y0, z0).setColor(255,255,255,255).setUv(u0,v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry,nx,ny,nz);
        buf.addVertex(pose, x1, y1, z1).setColor(255,255,255,255).setUv(u1,v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry,nx,ny,nz);
        buf.addVertex(pose, x2, y2, z2).setColor(255,255,255,255).setUv(u1,v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry,nx,ny,nz);
        buf.addVertex(pose, x3, y3, z3).setColor(255,255,255,255).setUv(u0,v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(entry,nx,ny,nz);
    }

    // ── Animation interpolation ────────────────────────────────────────────────

    private static float[] interpolateRotation(BBModelData model, String boneName, float time, float[] base) {
        for (BBModelData.Animation anim : model.animations().values()) {
            BBModelData.BoneAnimation boneAnim = anim.boneAnimations().get(boneName);
            if (boneAnim == null) continue;
            float[] result = interpolate(boneAnim.rotationFrames(), time, base);
            if (result != base) return result;
        }
        return base;
    }

    private static float[] interpolatePosition(BBModelData model, String boneName, float time) {
        float[] zero = {0, 0, 0};
        for (BBModelData.Animation anim : model.animations().values()) {
            BBModelData.BoneAnimation boneAnim = anim.boneAnimations().get(boneName);
            if (boneAnim == null) continue;
            float[] result = interpolate(boneAnim.positionFrames(), time, zero);
            if (result != zero) return result;
        }
        return zero;
    }

    private static float[] interpolate(List<BBModelData.Keyframe> frames, float time, float[] fallback) {
        if (frames.isEmpty()) return fallback;

        // Loop: wrap time into animation range
        float end = frames.get(frames.size() - 1).time();
        if (end > 0) time = time % end;

        BBModelData.Keyframe prev = frames.get(0);
        for (int i = 1; i < frames.size(); i++) {
            BBModelData.Keyframe next = frames.get(i);
            if (time <= next.time()) {
                float span = next.time() - prev.time();
                float t = span == 0 ? 0 : (time - prev.time()) / span;
                return lerp(prev.values(), next.values(), t);
            }
            prev = next;
        }
        return prev.values();
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        };
    }
}
