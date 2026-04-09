package io.github.manasmods.manas_cosmetics.client.renderer;

import io.github.manasmods.manas_cosmetics.api.CosmeticDefinition;
import io.github.manasmods.manas_cosmetics.core.bbmodel.BBModelData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of cosmetic definitions and parsed BBModelData.
 * Populated when the server syncs the cosmetic registry to the client on login.
 */
public final class ClientCosmeticModelCache {

    private static final ClientCosmeticModelCache INSTANCE = new ClientCosmeticModelCache();

    private final Map<String, CosmeticDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, BBModelData> models = new ConcurrentHashMap<>();

    private ClientCosmeticModelCache() {}

    public static ClientCosmeticModelCache get() { return INSTANCE; }

    public void register(CosmeticDefinition def, BBModelData model) {
        definitions.put(def.id(), def);
        models.put(def.id(), model);
    }

    public Optional<CosmeticDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<BBModelData> getModel(String id) {
        return Optional.ofNullable(models.get(id));
    }

    public void clear() {
        definitions.clear();
        models.clear();
        CosmeticLayer.evictTexture("*"); // signal full eviction
    }
}
