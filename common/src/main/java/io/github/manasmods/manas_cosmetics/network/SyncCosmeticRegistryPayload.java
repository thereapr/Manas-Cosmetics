package io.github.manasmods.manas_cosmetics.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.manasmods.manas_cosmetics.ManasCosmetics;
import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.core.CosmeticManager;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * S2C packet that delivers the full cosmetic registry (definitions + serialised BBModelData)
 * to a client on login or after a server-side reload.
 *
 * Serialisation strategy:
 *  - Each CosmeticDefinition is encoded as individual fields.
 *  - Each BBModelData is serialised to a compact JSON string and sent as UTF bytes.
 *    This avoids re-parsing on the client and keeps the format stable.
 */
public final class SyncCosmeticRegistryPayload implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(ManasCosmetics.MOD_ID, "sync_cosmetic_registry");

    public static final CustomPacketPayload.Type<SyncCosmeticRegistryPayload> TYPE =
        new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCosmeticRegistryPayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, payload) -> payload.encode(buf),
            SyncCosmeticRegistryPayload::decode
        );

    private static final Gson GSON = new GsonBuilder().create();

    public record Entry(CosmeticDefinition definition, String bbModelJson) {}

    private final List<Entry> entries;

    public SyncCosmeticRegistryPayload(List<Entry> entries) {
        this.entries = entries;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public List<Entry> getEntries() { return entries; }

    // ── Encode / Decode ────────────────────────────────────────────────────────

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            CosmeticDefinition d = e.definition();
            buf.writeUtf(d.id());
            buf.writeUtf(d.displayName());
            buf.writeUtf(d.slot().getId());
            buf.writeUtf(d.weaponType().getId());
            buf.writeBoolean(d.forceEquipAllowed());
            buf.writeUtf(d.modelPath());
            for (float v : d.scale())    buf.writeFloat(v);
            for (float v : d.offset())   buf.writeFloat(v);
            for (float v : d.rotation()) buf.writeFloat(v);
            // Empty string means no mob_type (preserves backward compat)
            buf.writeUtf(d.mobType() != null ? d.mobType() : "");
            buf.writeUtf(e.bbModelJson());
        }
    }

    public static SyncCosmeticRegistryPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id              = buf.readUtf();
            String displayName     = buf.readUtf();
            String slotId          = buf.readUtf();
            String weaponTypeId    = buf.readUtf();
            boolean forceEquip     = buf.readBoolean();
            String modelPath       = buf.readUtf();
            float[] scale    = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            float[] offset   = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            float[] rotation = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            String mobTypeRaw      = buf.readUtf();
            String mobType         = mobTypeRaw.isEmpty() ? null : mobTypeRaw;
            String bbModelJson     = buf.readUtf();

            var slot = io.github.manasmods.manas_cosmetics.api.CosmeticSlot.fromId(slotId)
                .orElse(io.github.manasmods.manas_cosmetics.api.CosmeticSlot.BACK);
            var weaponType = io.github.manasmods.manas_cosmetics.api.WeaponType.fromId(weaponTypeId);

            CosmeticDefinition def = new CosmeticDefinition(
                id, displayName, slot, weaponType, forceEquip, modelPath, scale, offset, rotation, mobType);
            entries.add(new Entry(def, bbModelJson));
        }
        return new SyncCosmeticRegistryPayload(entries);
    }

    // ── Serialise BBModelData ──────────────────────────────────────────────────

    /** Converts a {@link BBModelData} to a compact JSON string suitable for network transport. */
    public static String serialiseBBModel(BBModelData data) {
        JsonObject root = new JsonObject();
        root.addProperty("name", data.name());
        root.addProperty("texW", data.textureWidth());
        root.addProperty("texH", data.textureHeight());

        // Texture as Base64
        root.addProperty("tex", java.util.Base64.getEncoder().encodeToString(data.textureBytes()));

        // Bones
        root.add("bones", serialiseBones(data.bones()));

        // Animations
        JsonObject anims = new JsonObject();
        data.animations().forEach((name, anim) -> anims.add(name, serialiseAnimation(anim)));
        root.add("animations", anims);

        return GSON.toJson(root);
    }

    /** Reconstructs a {@link BBModelData} from the compact JSON string. */
    public static BBModelData deserialiseBBModel(String json) {
        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        String name  = root.get("name").getAsString();
        int texW     = root.get("texW").getAsInt();
        int texH     = root.get("texH").getAsInt();
        byte[] tex   = java.util.Base64.getDecoder().decode(root.get("tex").getAsString());

        List<BBModelData.Bone> bones = deserialiseBones(root.getAsJsonArray("bones"));

        java.util.Map<String, BBModelData.Animation> anims = new java.util.LinkedHashMap<>();
        if (root.has("animations")) {
            root.getAsJsonObject("animations").entrySet().forEach(e ->
                anims.put(e.getKey(), deserialiseAnimation(e.getValue().getAsJsonObject()))
            );
        }

        return new BBModelData(name, texW, texH, bones, tex, anims);
    }

    // ── Bone serialisation helpers ─────────────────────────────────────────────

    private static JsonArray serialiseBones(List<BBModelData.Bone> bones) {
        JsonArray arr = new JsonArray();
        for (BBModelData.Bone b : bones) {
            JsonObject o = new JsonObject();
            o.addProperty("name", b.name());
            o.add("pivot", vec3(b.pivot()));
            o.add("rot",   vec3(b.rotation()));
            o.add("cubes", serialiseCubes(b.cubes()));
            o.add("children", serialiseBones(b.children()));
            arr.add(o);
        }
        return arr;
    }

    private static List<BBModelData.Bone> deserialiseBones(JsonArray arr) {
        if (arr == null) return new ArrayList<>();
        List<BBModelData.Bone> list = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            list.add(new BBModelData.Bone(
                o.get("name").getAsString(),
                readVec3(o.getAsJsonArray("pivot")),
                readVec3(o.getAsJsonArray("rot")),
                deserialiseCubes(o.has("cubes") ? o.getAsJsonArray("cubes") : null),
                deserialiseBones(o.has("children") ? o.getAsJsonArray("children") : null)
            ));
        }
        return list;
    }

    private static JsonArray serialiseCubes(List<BBModelData.Cube> cubes) {
        JsonArray arr = new JsonArray();
        for (BBModelData.Cube c : cubes) {
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name());
            o.add("from",  vec3(c.from()));
            o.add("to",    vec3(c.to()));
            o.add("pivot", vec3(c.pivot()));
            o.add("rot",   vec3(c.rotation()));
            JsonObject faces = new JsonObject();
            c.faces().forEach((side, face) -> {
                JsonObject f = new JsonObject();
                JsonArray uv = new JsonArray();
                for (float v : face.uv()) uv.add(v);
                f.add("uv", uv);
                f.addProperty("tex", face.textureIndex());
                faces.add(side, f);
            });
            o.add("faces", faces);
            arr.add(o);
        }
        return arr;
    }

    private static List<BBModelData.Cube> deserialiseCubes(JsonArray arr) {
        if (arr == null) return new ArrayList<>();
        List<BBModelData.Cube> list = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            java.util.Map<String, BBModelData.Face> faces = new java.util.LinkedHashMap<>();
            JsonObject facesObj = o.has("faces") ? o.getAsJsonObject("faces") : null;
            if (facesObj != null) {
                facesObj.entrySet().forEach(e -> {
                    JsonObject f = e.getValue().getAsJsonObject();
                    JsonArray uvArr = f.has("uv") ? f.getAsJsonArray("uv") : null;
                    if (uvArr != null && uvArr.size() >= 4) {
                        float[] uv = readVec4(uvArr);
                        faces.put(e.getKey(), new BBModelData.Face(uv, f.get("tex").getAsInt()));
                    }
                });
            }
            list.add(new BBModelData.Cube(
                o.get("name").getAsString(),
                readVec3(o.getAsJsonArray("from")),
                readVec3(o.getAsJsonArray("to")),
                readVec3(o.getAsJsonArray("pivot")),
                readVec3(o.getAsJsonArray("rot")),
                faces
            ));
        }
        return list;
    }

    // ── Animation serialisation helpers ────────────────────────────────────────

    private static JsonObject serialiseAnimation(BBModelData.Animation anim) {
        JsonObject o = new JsonObject();
        o.addProperty("name", anim.name());
        o.addProperty("loop", anim.loop());
        o.addProperty("length", anim.animationLength());
        JsonObject bones = new JsonObject();
        anim.boneAnimations().forEach((bone, ba) -> {
            JsonObject b = new JsonObject();
            b.add("rot", serialiseKeyframes(ba.rotationFrames()));
            b.add("pos", serialiseKeyframes(ba.positionFrames()));
            b.add("scale", serialiseKeyframes(ba.scaleFrames()));
            bones.add(bone, b);
        });
        o.add("bones", bones);
        return o;
    }

    private static BBModelData.Animation deserialiseAnimation(JsonObject o) {
        java.util.Map<String, BBModelData.BoneAnimation> bones = new java.util.LinkedHashMap<>();
        JsonObject bonesObj = o.has("bones") ? o.getAsJsonObject("bones") : null;
        if (bonesObj != null) {
            bonesObj.entrySet().forEach(e -> {
                JsonObject b = e.getValue().getAsJsonObject();
                bones.put(e.getKey(), new BBModelData.BoneAnimation(
                    deserialiseKeyframes(b.has("rot")   ? b.getAsJsonArray("rot")   : null),
                    deserialiseKeyframes(b.has("pos")   ? b.getAsJsonArray("pos")   : null),
                    deserialiseKeyframes(b.has("scale") ? b.getAsJsonArray("scale") : null)
                ));
            });
        }
        return new BBModelData.Animation(
            o.get("name").getAsString(),
            o.get("loop").getAsBoolean(),
            o.get("length").getAsDouble(),
            bones
        );
    }

    private static JsonArray serialiseKeyframes(List<BBModelData.Keyframe> frames) {
        JsonArray arr = new JsonArray();
        for (BBModelData.Keyframe kf : frames) {
            JsonObject o = new JsonObject();
            o.addProperty("t", kf.time());
            o.add("v", vec3(kf.values()));
            arr.add(o);
        }
        return arr;
    }

    private static List<BBModelData.Keyframe> deserialiseKeyframes(JsonArray arr) {
        if (arr == null) return new ArrayList<>();
        List<BBModelData.Keyframe> list = new ArrayList<>();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            list.add(new BBModelData.Keyframe(o.get("t").getAsFloat(), readVec3(o.getAsJsonArray("v"))));
        }
        return list;
    }

    // ── Primitive helpers ──────────────────────────────────────────────────────

    private static JsonArray vec3(float[] v) {
        JsonArray a = new JsonArray();
        a.add(v[0]); a.add(v[1]); a.add(v[2]);
        return a;
    }

    private static float[] readVec3(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] readVec4(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()};
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    /** Builds the payload from the current server-side {@link CosmeticManager} state. */
    public static SyncCosmeticRegistryPayload fromManager() {
        Collection<CosmeticDefinition> defs = CosmeticManager.get().getAllDefinitions();
        List<Entry> entries = new ArrayList<>(defs.size());
        for (CosmeticDefinition def : defs) {
            CosmeticManager.get().getModel(def.id()).ifPresent(model ->
                entries.add(new Entry(def, serialiseBBModel(model)))
            );
        }
        return new SyncCosmeticRegistryPayload(entries);
    }
}
