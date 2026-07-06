package dev.akivcraft.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomCarverRegistry {
    private static volatile Registry<ConfiguredWorldCarver<?>> frozenRegistry;

    private CustomCarverRegistry() {
    }

    public static Registry<ConfiguredWorldCarver<?>> frozenRegistry() {
        return frozenRegistry;
    }

    @SuppressWarnings("unchecked")
    public static void registerFromFile(Path file, Object registryObject) {
        var registry = (Registry<ConfiguredWorldCarver<?>>) registryObject;
        frozenRegistry = registry;
        if (!Files.isRegularFile(file)) return;

        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var carvers = root.getAsJsonArray("carvers");
            if (carvers == null) return;

            var registered = 0;
            for (var element : carvers) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                var id = stringValue(obj, "id", null);
                if (id == null || id.isBlank()) continue;

                var identifier = Identifier.parse(id);
                if (registry.containsKey(identifier)) continue;

                var carverJson = new JsonObject();
                carverJson.addProperty("type", stringValue(obj, "type", "minecraft:cave"));
                var config = obj.get("config");
                if (config != null && config.isJsonObject()) {
                    carverJson.add("config", config.getAsJsonObject());
                }

                try {
                    var decoded = ConfiguredWorldCarver.DIRECT_CODEC.decode(JsonOps.INSTANCE, carverJson);
                    var pair = decoded.result().orElse(null);
                    if (pair == null) {
                        System.err.printf("AkivCraft failed to decode carver %s%n", id);
                        continue;
                    }
                    var carver = pair.getFirst();
                    var key = ResourceKey.create(Registries.CONFIGURED_CARVER, identifier);
                    Registry.register(registry, key, carver);
                    registered++;
                } catch (Throwable error) {
                    System.err.printf("AkivCraft failed to register carver %s: %s%n", id, error.getMessage());
                }
            }

            if (registered > 0) {
                AkivCraftLoadingLog.info("Registered " + registered + " custom carvers");
                System.out.printf("AkivCraft registered %d custom carvers%n", registered);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to read carvers from %s: %s%n", file, error.getMessage());
        }
    }

    private static String stringValue(JsonObject obj, String name, String fallback) {
        var element = obj.get(name);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return element.getAsString();
    }
}
