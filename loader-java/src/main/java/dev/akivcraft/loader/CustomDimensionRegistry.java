package dev.akivcraft.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        System.out.printf("AkivCraft registerTypesFromFile called: %s exists=%s%n", file, Files.isRegularFile(file));
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

                try {
                    var template = selectTemplateDimensionType(registry, obj);
                    if (template == null) {
                        System.err.printf("AkivCraft failed to resolve template dimension type for %s%n", id);
                        continue;
                    }
                    var typeConfig = obj.getAsJsonObject("type");
                    var monsterSettings = new DimensionType.MonsterSettings(
                        template.monsterSpawnLightTest(),
                        intValue(typeConfig, "monsterSpawnBlockLightLimit", template.monsterSpawnBlockLightLimit())
                    );
                    var dimensionType = new DimensionType(
                        template.hasFixedTime(),
                        booleanValue(typeConfig, "hasSkylight", template.hasSkyLight()),
                        booleanValue(typeConfig, "hasCeiling", template.hasCeiling()),
                        template.hasEnderDragonFight(),
                        numberValue(typeConfig, "coordinateScale", template.coordinateScale()).doubleValue(),
                        intValue(typeConfig, "minY", template.minY()),
                        intValue(typeConfig, "height", template.height()),
                        intValue(typeConfig, "logicalHeight", template.logicalHeight()),
                        template.infiniburn(),
                        numberValue(typeConfig, "ambientLight", template.ambientLight()).floatValue(),
                        monsterSettings,
                        template.skybox(),
                        template.cardinalLightType(),
                        template.attributes(),
                        template.timelines(),
                        template.defaultClock()
                    );
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
        System.out.printf("AkivCraft registerStemsFromFile called: %s exists=%s%n", file, Files.isRegularFile(file));
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

    private static DimensionType selectTemplateDimensionType(Registry<DimensionType> registry, JsonObject obj) {
        var generator = obj.getAsJsonObject("generator");
        var template = generator != null ? stringValue(generator, "template", "overworld") : "overworld";
        var key = switch (template.toLowerCase(java.util.Locale.ROOT)) {
            case "nether" -> Identifier.parse("minecraft:the_nether");
            case "end" -> Identifier.parse("minecraft:the_end");
            default -> Identifier.parse("minecraft:overworld");
        };
        return registry.get(key).map(Holder.Reference::value).orElse(null);
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
