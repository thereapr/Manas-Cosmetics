package io.github.manasmods.manas_cosmetics.core.bbmodel;

import java.util.List;
import java.util.Map;

/**
 * Holds all data parsed from a .bbmodel file:
 * - Bone hierarchy (groups of cubes)
 * - Raw texture bytes decoded from the embedded base64 PNG
 * - Animations keyed by name
 */
public record BBModelData(
    String name,
    int textureWidth,
    int textureHeight,
    List<Bone> bones,
    byte[] textureBytes,
    Map<String, Animation> animations
) {

    public record Bone(
        String name,
        float[] pivot,
        float[] rotation,
        List<Cube> cubes,
        List<Bone> children
    ) {}

    public record Cube(
        String name,
        float[] from,
        float[] to,
        float[] pivot,
        float[] rotation,
        Map<String, Face> faces
    ) {}

    public record Face(
        float[] uv,
        int textureIndex
    ) {}

    public record Keyframe(float time, float[] values) {}

    public record BoneAnimation(
        List<Keyframe> rotationFrames,
        List<Keyframe> positionFrames,
        List<Keyframe> scaleFrames
    ) {}

    public record Animation(
        String name,
        boolean loop,
        double animationLength,
        Map<String, BoneAnimation> boneAnimations
    ) {}
}
