package dev.akivcraft.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomFeatureRegistry {
    private static volatile Registry<PlacedFeature> frozenRegistry;

    private CustomFeatureRegistry() {
    }

    public static Registry<PlacedFeature> frozenRegistry() {
        return frozenRegistry;
    }

    @SuppressWarnings("unchecked")
    public static void registerFromFile(Path file, Object registryObject) {
        var registry = (Registry<PlacedFeature>) registryObject;
        frozenRegistry = registry;
        if (!Files.isRegularFile(file)) return;

        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var features = root.getAsJsonArray("features");
            if (features == null) return;

            var registered = 0;
            for (var element : features) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                var id = stringValue(obj, "id", null);
                if (id == null || id.isBlank()) continue;

                var identifier = Identifier.parse(id);
                if (registry.containsKey(identifier)) continue;

                var placedJson = new JsonObject();
                var featureObj = new JsonObject();
                featureObj.addProperty("type", stringValue(obj, "type", "minecraft:no_op"));
                var config = obj.get("config");
                if (config != null && config.isJsonObject()) {
                    featureObj.add("config", config.getAsJsonObject());
                }
                placedJson.add("feature", featureObj);
                placedJson.add("placement", new com.google.gson.JsonArray());

                try {
                    var decoded = PlacedFeature.DIRECT_CODEC.decode(JsonOps.INSTANCE, placedJson);
                    var pair = decoded.result().orElse(null);
                    if (pair == null) {
                        System.err.printf("AkivCraft failed to decode feature %s%n", id);
                        continue;
                    }
                    var placedFeature = pair.getFirst();
                    var key = ResourceKey.create(Registries.PLACED_FEATURE, identifier);
                    Registry.register(registry, key, placedFeature);
                    registered++;
                } catch (Throwable error) {
                    System.err.printf("AkivCraft failed to register feature %s: %s%n", id, error.getMessage());
                }
            }

            if (registered > 0) {
                AkivCraftLoadingLog.info("Registered " + registered + " custom placed features");
                System.out.printf("AkivCraft registered %d custom placed features%n", registered);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to read features from %s: %s%n", file, error.getMessage());
        }
    }

    private static String stringValue(JsonObject obj, String name, String fallback) {
        var element = obj.get(name);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return element.getAsString();
    }
}
