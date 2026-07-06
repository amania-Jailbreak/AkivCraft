package dev.akivcraft.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CustomDimensionRegistry {
    private static volatile Registry<DimensionType> dimensionTypeRegistry;

    private CustomDimensionRegistry() {
    }

    @SuppressWarnings("unchecked")
    public static void registerTypesFromFile(Path file, Object registryObject) {
        var registry = (Registry<DimensionType>) registryObject;
        dimensionTypeRegistry = registry;
        if (!Files.isRegularFile(file)) return;

        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var dimensions = root.getAsJsonArray("dimensions");
            if (dimensions == null) return;

            var registered = 0;
            for (var element : dimensions) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                var id = stringValue(obj, "id", null);
                if (id == null || id.isBlank()) continue;

                var identifier = Identifier.parse(id);
                if (registry.containsKey(identifier)) continue;

                var typeJson = buildDimensionTypeJson(obj.getAsJsonObject("type"));
                try {
                    var decoded = DimensionType.DIRECT_CODEC.decode(JsonOps.INSTANCE, typeJson);
                    var pair = decoded.result().orElse(null);
                    if (pair == null) {
                        System.err.printf("AkivCraft failed to decode dimension type %s%n", id);
                        continue;
                    }
                    var dimensionType = pair.getFirst();
                    var key = ResourceKey.create(Registries.DIMENSION_TYPE, identifier);
                    Registry.register(registry, key, dimensionType);
                    registered++;
                    System.out.printf("AkivCraft registered dimension type %s%n", id);
                } catch (Throwable error) {
                    System.err.printf("AkivCraft failed to register dimension type %s: %s%n", id, error.getMessage());
                }
            }
            if (registered > 0) {
                AkivCraftLoadingLog.info("Registered " + registered + " custom dimension types");
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to read dimensions from %s: %s%n", file, error.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static void registerStemsFromFile(Path file, Object registryObject) {
        var registry = (Registry<LevelStem>) registryObject;
        if (!Files.isRegularFile(file)) return;

        var overworldStem = registry.get(LevelStem.OVERWORLD).map(Holder.Reference::value).orElse(null);
        var netherStem = registry.get(LevelStem.NETHER).map(Holder.Reference::value).orElse(null);
        var endStem = registry.get(LevelStem.END).map(Holder.Reference::value).orElse(null);

        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var dimensions = root.getAsJsonArray("dimensions");
            if (dimensions == null) return;

            var registered = 0;
            for (var element : dimensions) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                var id = stringValue(obj, "id", null);
                if (id == null || id.isBlank()) continue;

                var identifier = Identifier.parse(id);
                if (registry.containsKey(identifier)) continue;
                if (dimensionTypeRegistry == null) continue;

                var dimTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE, identifier);
                var dimTypeHolder = dimensionTypeRegistry.get(dimTypeKey);
                if (dimTypeHolder.isEmpty()) {
                    System.err.printf("AkivCraft dimension type %s not found for level stem; skipping%n", id);
                    continue;
                }

                var generator = selectGenerator(obj, overworldStem, netherStem, endStem);
                if (generator == null) {
                    System.err.printf("AkivCraft no generator template for dimension %s; skipping%n", id);
                    continue;
                }

                var stem = new LevelStem(dimTypeHolder.get(), generator);
                var stemKey = ResourceKey.create(Registries.LEVEL_STEM, identifier);
                Registry.register(registry, stemKey, stem);
                registered++;
                System.out.printf("AkivCraft registered level stem %s%n", id);
            }
            if (registered > 0) {
                AkivCraftLoadingLog.info("Registered " + registered + " custom level stems");
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to register level stems from %s: %s%n", file, error.getMessage());
        }
    }

    private static net.minecraft.world.level.chunk.ChunkGenerator selectGenerator(
        JsonObject obj,
        LevelStem overworldStem,
        LevelStem netherStem,
        LevelStem endStem
    ) {
        var generator = obj.getAsJsonObject("generator");
        var template = generator != null ? stringValue(generator, "template", "overworld") : "overworld";
        return switch (template.toLowerCase(java.util.Locale.ROOT)) {
            case "nether" -> netherStem != null ? netherStem.generator() : null;
            case "end" -> endStem != null ? endStem.generator() : null;
            default -> overworldStem != null ? overworldStem.generator() : null;
        };
    }

    private static JsonObject buildDimensionTypeJson(JsonObject type) {
        if (type == null) type = new JsonObject();
        var json = new JsonObject();
        json.addProperty("ultrawarm", booleanValue(type, "ultrawarm", false));
        json.addProperty("natural", booleanValue(type, "natural", true));
        json.addProperty("piglin_safe", booleanValue(type, "piglinSafe", false));
        json.addProperty("respawn_anchor_works", booleanValue(type, "respawnAnchorWorks", false));
        json.addProperty("has_skylight", booleanValue(type, "hasSkylight", true));
        json.addProperty("has_ceiling", booleanValue(type, "hasCeiling", false));
        json.addProperty("ambient_light", numberValue(type, "ambientLight", 0.0));
        json.addProperty("fixed_time", type.has("fixedTime") ? type.get("fixedTime").getAsNumber() : null);
        json.addProperty("monster_spawn_block_light_limit", intValue(type, "monsterSpawnBlockLightLimit", 0));
        json.addProperty("infiniburn", stringValue(type, "infiniburn", "#minecraft:infiniburn_overworld"));
        json.addProperty("effects", stringValue(type, "effects", "minecraft:overworld"));
        json.addProperty("height", intValue(type, "height", 256));
        json.addProperty("min_y", intValue(type, "minY", 0));
        json.addProperty("logical_height", intValue(type, "logicalHeight", intValue(type, "height", 256)));
        json.addProperty("coordinate_scale", numberValue(type, "coordinateScale", 1.0));
        json.addProperty("bed_works", booleanValue(type, "bedWorks", true));
        json.addProperty("has_raids", booleanValue(type, "hasRaids", true));
        return json;
    }

    private static String stringValue(JsonObject obj, String name, String fallback) {
        var element = obj.get(name);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return element.getAsString();
    }

    private static boolean booleanValue(JsonObject obj, String name, boolean fallback) {
        var element = obj.get(name);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return element.getAsBoolean();
    }

    private static int intValue(JsonObject obj, String name, int fallback) {
        var element = obj.get(name);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return element.getAsInt();
    }

    private static Number numberValue(JsonObject obj, String name, double fallback) {
        var element = obj.get(name);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return element.getAsNumber();
    }
}
