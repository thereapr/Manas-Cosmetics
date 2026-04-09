package io.github.manasmods.manas_cosmetics.core.bbmodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Parses a Blockbench .bbmodel JSON file into {@link BBModelData}.
 *
 * The .bbmodel format stores geometry as cube elements in a flat list, referenced by UUID
 * from a tree of bone groups ("outliner"). Textures are embedded as base64-encoded PNGs.
 * Animations store per-bone keyframes for rotation, position, and scale.
 */
public final class BBModelParser {

    private BBModelParser() {}

    public static BBModelData parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        String name = root.has("name") ? root.get("name").getAsString() : "unnamed";

        // Resolution
        int texW = 16, texH = 16;
        if (root.has("resolution")) {
            JsonObject res = root.getAsJsonObject("resolution");
            texW = res.get("width").getAsInt();
            texH = res.get("height").getAsInt();
        }

        // Build UUID → element map
        Map<String, JsonObject> elementMap = new HashMap<>();
        if (root.has("elements")) {
            for (JsonElement el : root.getAsJsonArray("elements")) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("uuid")) {
                    elementMap.put(obj.get("uuid").getAsString(), obj);
                }
            }
        }

        // Parse bone hierarchy from outliner
        List<BBModelData.Bone> roots = new ArrayList<>();
        if (root.has("outliner")) {
            for (JsonElement el : root.getAsJsonArray("outliner")) {
                if (el.isJsonObject()) {
                    roots.add(parseBone(el.getAsJsonObject(), elementMap, texW, texH));
                }
            }
        }

        // Decode first texture (base64 PNG)
        byte[] textureBytes = new byte[0];
        if (root.has("textures")) {
            JsonArray textures = root.getAsJsonArray("textures");
            if (!textures.isEmpty()) {
                JsonObject tex = textures.get(0).getAsJsonObject();
                if (tex.has("source")) {
                    String source = tex.get("source").getAsString();
                    // Strip "data:image/png;base64," prefix
                    int comma = source.indexOf(',');
                    if (comma >= 0) {
                        source = source.substring(comma + 1);
                    }
                    textureBytes = Base64.getDecoder().decode(source);
                }
            }
        }

        // Parse animations
        Map<String, BBModelData.Animation> animations = new LinkedHashMap<>();
        if (root.has("animations")) {
            for (JsonElement el : root.getAsJsonArray("animations")) {
                BBModelData.Animation anim = parseAnimation(el.getAsJsonObject());
                animations.put(anim.name(), anim);
            }
        }

        return new BBModelData(name, texW, texH, roots, textureBytes, animations);
    }

    private static BBModelData.Bone parseBone(JsonObject obj, Map<String, JsonObject> elementMap, int texW, int texH) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "bone";
        float[] pivot = readVec3(obj, "origin");
        float[] rotation = readVec3(obj, "rotation");

        List<BBModelData.Cube> cubes = new ArrayList<>();
        List<BBModelData.Bone> children = new ArrayList<>();

        if (obj.has("children")) {
            for (JsonElement child : obj.getAsJsonArray("children")) {
                if (child.isJsonPrimitive()) {
                    // UUID reference to an element
                    String uuid = child.getAsString();
                    JsonObject el = elementMap.get(uuid);
                    if (el != null) {
                        cubes.add(parseCube(el, texW, texH));
                    }
                } else if (child.isJsonObject()) {
                    children.add(parseBone(child.getAsJsonObject(), elementMap, texW, texH));
                }
            }
        }

        return new BBModelData.Bone(name, pivot, rotation, cubes, children);
    }

    private static BBModelData.Cube parseCube(JsonObject obj, int texW, int texH) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "cube";
        float[] from = readVec3(obj, "from");
        float[] to = readVec3(obj, "to");
        float[] pivot = readVec3(obj, "origin");
        float[] rotation = readVec3(obj, "rotation");

        Map<String, BBModelData.Face> faces = new LinkedHashMap<>();
        if (obj.has("faces")) {
            JsonObject facesObj = obj.getAsJsonObject("faces");
            for (String side : new String[]{"north", "south", "east", "west", "up", "down"}) {
                if (facesObj.has(side)) {
                    faces.put(side, parseFace(facesObj.getAsJsonObject(side)));
                }
            }
        }

        return new BBModelData.Cube(name, from, to, pivot, rotation, faces);
    }

    private static BBModelData.Face parseFace(JsonObject obj) {
        float[] uv = {0, 0, 16, 16};
        if (obj.has("uv")) {
            JsonArray arr = obj.getAsJsonArray("uv");
            uv = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(),
                             arr.get(2).getAsFloat(), arr.get(3).getAsFloat()};
        }
        int texIdx = obj.has("texture") && !obj.get("texture").isJsonNull()
            ? obj.get("texture").getAsInt() : 0;
        return new BBModelData.Face(uv, texIdx);
    }

    private static BBModelData.Animation parseAnimation(JsonObject obj) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "animation";
        boolean loop = obj.has("loop") && !obj.get("loop").getAsString().equals("once");
        double length = obj.has("length") ? obj.get("length").getAsDouble() : 0;

        Map<String, BBModelData.BoneAnimation> boneAnimations = new LinkedHashMap<>();
        if (obj.has("bones")) {
            JsonObject bones = obj.getAsJsonObject("bones");
            for (String boneName : bones.keySet()) {
                boneAnimations.put(boneName, parseBoneAnimation(bones.getAsJsonObject(boneName)));
            }
        } else if (obj.has("animators")) {
            // Newer bbmodel format stores per-bone keyframes under "animators"
            JsonObject animators = obj.getAsJsonObject("animators");
            for (String boneKey : animators.keySet()) {
                JsonObject animator = animators.getAsJsonObject(boneKey);
                String boneName = animator.has("name") ? animator.get("name").getAsString() : boneKey;
                boneAnimations.put(boneName, parseBoneAnimatorKeyframes(animator));
            }
        }

        return new BBModelData.Animation(name, loop, length, boneAnimations);
    }

    /** Parses old-style bone animations where keys are timestamps and values are [x,y,z] arrays. */
    private static BBModelData.BoneAnimation parseBoneAnimation(JsonObject obj) {
        List<BBModelData.Keyframe> rotFrames = parseTimestampChannel(obj, "rotation");
        List<BBModelData.Keyframe> posFrames = parseTimestampChannel(obj, "position");
        List<BBModelData.Keyframe> scaleFrames = parseTimestampChannel(obj, "scale");
        return new BBModelData.BoneAnimation(rotFrames, posFrames, scaleFrames);
    }

    private static List<BBModelData.Keyframe> parseTimestampChannel(JsonObject bone, String channel) {
        List<BBModelData.Keyframe> frames = new ArrayList<>();
        if (!bone.has(channel)) return frames;
        JsonElement ch = bone.get(channel);
        if (!ch.isJsonObject()) return frames;
        for (Map.Entry<String, JsonElement> entry : ch.getAsJsonObject().entrySet()) {
            float time;
            try { time = Float.parseFloat(entry.getKey()); } catch (NumberFormatException e) { continue; }
            float[] values = readVec3Array(entry.getValue());
            frames.add(new BBModelData.Keyframe(time, values));
        }
        frames.sort(Comparator.comparingDouble(BBModelData.Keyframe::time));
        return frames;
    }

    /** Parses new-style bone animators that store a list of keyframe objects. */
    private static BBModelData.BoneAnimation parseBoneAnimatorKeyframes(JsonObject animator) {
        List<BBModelData.Keyframe> rotFrames = new ArrayList<>();
        List<BBModelData.Keyframe> posFrames = new ArrayList<>();
        List<BBModelData.Keyframe> scaleFrames = new ArrayList<>();

        if (animator.has("keyframes")) {
            for (JsonElement kfEl : animator.getAsJsonArray("keyframes")) {
                JsonObject kf = kfEl.getAsJsonObject();
                float time = kf.has("time") ? kf.get("time").getAsFloat() : 0f;
                String channel = kf.has("channel") ? kf.get("channel").getAsString() : "rotation";
                float[] values = {0, 0, 0};
                if (kf.has("data_points")) {
                    JsonArray dp = kf.getAsJsonArray("data_points");
                    if (!dp.isEmpty()) {
                        values = readVec3FromDataPoint(dp.get(0).getAsJsonObject());
                    }
                }
                BBModelData.Keyframe frame = new BBModelData.Keyframe(time, values);
                switch (channel) {
                    case "rotation" -> rotFrames.add(frame);
                    case "position" -> posFrames.add(frame);
                    case "scale" -> scaleFrames.add(frame);
                }
            }
        }

        rotFrames.sort(Comparator.comparingDouble(BBModelData.Keyframe::time));
        posFrames.sort(Comparator.comparingDouble(BBModelData.Keyframe::time));
        scaleFrames.sort(Comparator.comparingDouble(BBModelData.Keyframe::time));

        return new BBModelData.BoneAnimation(rotFrames, posFrames, scaleFrames);
    }

    private static float[] readVec3(JsonObject obj, String key) {
        if (!obj.has(key)) return new float[]{0, 0, 0};
        return readVec3Array(obj.get(key));
    }

    private static float[] readVec3Array(JsonElement el) {
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
        }
        return new float[]{0, 0, 0};
    }

    private static float[] readVec3FromDataPoint(JsonObject dp) {
        return new float[]{
            dp.has("x") ? dp.get("x").getAsFloat() : 0f,
            dp.has("y") ? dp.get("y").getAsFloat() : 0f,
            dp.has("z") ? dp.get("z").getAsFloat() : 0f
        };
    }
}
