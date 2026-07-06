package dev.akivcraft.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class CustomBiomeRegistry {
    private static final Set<MultiNoiseBiomeSourceParameterList> injected = Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<MultiNoiseBiomeSourceParameterList, InjectionContext> contexts = new WeakHashMap<>();
    private static volatile boolean loaded;
    private static List<BiomePlacement> placements = List.of();
    private static List<BiomeDefinition> definitions = List.of();
    private static Field parametersField;

    private CustomBiomeRegistry() {
    }

    public static void registerFromFile(Path file, Object registryObject) {
        if (!Files.isRegularFile(file)) return;

        try {
            @SuppressWarnings("unchecked")
            var registry = (Registry<Biome>) registryObject;
            var loadedDefinitions = readDefinitions(file);
            definitions = loadedDefinitions;
            placements = loadedDefinitions.stream().map(BiomeDefinition::placement).toList();

            var registered = 0;
            for (var definition : loadedDefinitions) {
                var id = Identifier.parse(definition.id());
                if (registry.containsKey(id)) continue;
                var key = ResourceKey.create(Registries.BIOME, id);
                Registry.register(registry, key, definition.toBiome());
                registered++;
            }

            loaded = true;
            AkivCraftLoadingLog.info("Registered " + registered + " custom biomes");
            System.out.printf("AkivCraft registered %d custom biomes before BIOME registry freeze%n", registered);
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Custom biome registration failed: " + error.getMessage());
            System.err.printf("AkivCraft failed to register custom biomes: %s%n", error.getMessage());
            error.printStackTrace();
            if (error instanceof RuntimeException runtimeException) throw runtimeException;
            throw new AkivCraftStartupException("Custom biome registration failed", error);
        }
    }

    public static void remember(MultiNoiseBiomeSourceParameterList list, MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomeGetter) {
        if (list == null || preset == null || biomeGetter == null) return;
        synchronized (contexts) {
            contexts.put(list, new InjectionContext(preset, biomeGetter));
        }
    }

    public static void injectIfReady(MultiNoiseBiomeSourceParameterList list) {
        InjectionContext context;
        synchronized (contexts) {
            context = contexts.get(list);
        }
        if (context == null) return;
        injectInto(list, context.preset(), context.biomeGetter());
    }

    private static void injectInto(MultiNoiseBiomeSourceParameterList list, MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomeGetter) {
        try {
            ensureLoaded();
            if (placements.isEmpty() || list == null || preset == null || biomeGetter == null) return;

            synchronized (injected) {
                if (injected.contains(list)) return;
            }

            var presetId = preset.id().toString();
            var additions = new ArrayList<Pair<Climate.ParameterPoint, Holder<Biome>>>();
            for (var placement : placements) {
                if (!placement.matches(presetId)) continue;
                var key = ResourceKey.create(Registries.BIOME, Identifier.parse(placement.id()));
                Holder<Biome> holder;
                try {
                    holder = biomeGetter.get(key).orElse(null);
                } catch (IllegalStateException error) {
                    System.out.printf("AkivCraft custom biome %s is missing from frozen biome registry; skipping noise injection%n", placement.id());
                    continue;
                }
                if (holder == null || !holder.isBound()) {
                    // Dynamic biome registries can expose holders before the JSON-backed value is bound.
                    // Try again on a later parameters() call instead of leaving an unbound holder behind.
                    System.out.printf("AkivCraft custom biome %s is not bound yet; delaying noise injection%n", placement.id());
                    continue;
                }
                additions.add(Pair.of(placement.parameterPoint(), holder));
            }

            if (additions.isEmpty()) return;

            @SuppressWarnings("unchecked")
            var original = (Climate.ParameterList<Holder<Biome>>) parametersField().get(list);
            var combined = new ArrayList<Pair<Climate.ParameterPoint, Holder<Biome>>>(original.values());
            combined.addAll(additions);
            parametersField().set(list, new Climate.ParameterList<>(combined));
            synchronized (injected) {
                injected.add(list);
            }
            AkivCraftLoadingLog.info("Injected " + additions.size() + " custom biome placements into " + presetId);
            System.out.printf("AkivCraft injected %d custom biome placements into %s%n", additions.size(), presetId);
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Biome noise injection failed: " + error.getMessage());
            System.err.printf("AkivCraft failed to inject custom biomes: %s%n", error.getMessage());
            error.printStackTrace();
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (CustomBiomeRegistry.class) {
            if (loaded) return;
            loaded = true;
            var config = LoaderConfig.fromSystemProperties();
            var file = config.modsDirectory().resolve("loaded-biomes.json");
            definitions = readDefinitions(file);
            placements = definitions.stream().map(BiomeDefinition::placement).toList();
            if (!placements.isEmpty()) {
                AkivCraftLoadingLog.info("Loaded " + placements.size() + " custom biome placements");
                System.out.printf("AkivCraft loaded %d custom biome placements from %s%n", placements.size(), file.getFileName());
            }
        }
    }

    private static List<BiomeDefinition> readDefinitions(Path file) {
        if (!Files.isRegularFile(file)) return List.of();
        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var biomes = root.getAsJsonArray("biomes");
            if (biomes == null) return List.of();

            var result = new ArrayList<BiomeDefinition>();
            for (var element : biomes) {
                if (!element.isJsonObject()) continue;
                var obj = element.getAsJsonObject();
                var id = stringValue(obj.get("id"), null);
                var noise = obj.getAsJsonObject("noise");
                if (id == null || id.isBlank() || noise == null) continue;
                var placement = new BiomePlacement(
                    id,
                    stringValue(obj.get("source"), "overworld"),
                    range(noise.get("temperature"), -1f, 1f),
                    range(noise.get("humidity"), -1f, 1f),
                    range(noise.get("continentalness"), -1f, 1f),
                    range(noise.get("erosion"), -1f, 1f),
                    range(noise.get("depth"), -1f, 1f),
                    range(noise.get("weirdness"), -1f, 1f),
                    floatValue(noise.get("offset"), 0f)
                );
                result.add(new BiomeDefinition(id, obj, placement));
            }
            return List.copyOf(result);
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to read custom biomes from %s: %s%n", file, error.getMessage());
            return List.of();
        }
    }

    private static Range range(JsonElement element, float fallbackMin, float fallbackMax) {
        if (element instanceof JsonArray array && array.size() >= 2) {
            return new Range(array.get(0).getAsFloat(), array.get(1).getAsFloat());
        }
        if (element != null && element.isJsonPrimitive()) {
            var value = element.getAsFloat();
            return new Range(value, value);
        }
        return new Range(fallbackMin, fallbackMax);
    }

    private static String stringValue(JsonElement element, String fallback) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static float floatValue(JsonElement element, float fallback) {
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String field, boolean fallback) {
        var element = object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static int intValue(JsonObject object, String field, int fallback) {
        var element = object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static float floatValue(JsonObject object, String field, float fallback) {
        var element = object.get(field);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static String stringValue(JsonObject object, String field, String fallback) {
        return stringValue(object.get(field), fallback);
    }

    private static int colorValue(JsonObject object, String field, int fallback) {
        var value = stringValue(object, field, null);
        if (value == null || value.isBlank()) return fallback;
        try {
            var hex = value.startsWith("#") ? value.substring(1) : value;
            if (hex.length() == 8) hex = hex.substring(2);
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Biome.TemperatureModifier temperatureModifier(String value) {
        if ("frozen".equalsIgnoreCase(value)) return Biome.TemperatureModifier.FROZEN;
        return Biome.TemperatureModifier.NONE;
    }

    private static BiomeSpecialEffects.GrassColorModifier grassColorModifier(String value) {
        if (value == null) return BiomeSpecialEffects.GrassColorModifier.NONE;
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "dark_forest" -> BiomeSpecialEffects.GrassColorModifier.DARK_FOREST;
            case "swamp" -> BiomeSpecialEffects.GrassColorModifier.SWAMP;
            default -> BiomeSpecialEffects.GrassColorModifier.NONE;
        };
    }

    private static MobCategory mobCategory(String value) {
        for (var category : MobCategory.values()) {
            if (category.getSerializedName().equals(value)) return category;
        }
        return null;
    }

    private static Field parametersField() throws NoSuchFieldException {
        if (parametersField == null) {
            parametersField = MultiNoiseBiomeSourceParameterList.class.getDeclaredField("parameters");
            parametersField.setAccessible(true);
        }
        return parametersField;
    }

    private record Range(float min, float max) {
        Climate.Parameter parameter() {
            return Climate.Parameter.span(min, max);
        }
    }

    private record InjectionContext(MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomeGetter) {
    }

    private record BiomeDefinition(String id, JsonObject json, BiomePlacement placement) {
        Biome toBiome() {
            var effects = new BiomeSpecialEffects.Builder()
                .waterColor(colorValue(json, "waterColor", 0x3f76e4));
            if (json.has("grassColor")) effects.grassColorOverride(colorValue(json, "grassColor", 0));
            if (json.has("foliageColor")) effects.foliageColorOverride(colorValue(json, "foliageColor", 0));
            if (json.has("dryFoliageColor")) effects.dryFoliageColorOverride(colorValue(json, "dryFoliageColor", 0));
            effects.grassColorModifier(grassColorModifier(stringValue(json, "grassColorModifier", null)));

            var spawns = new MobSpawnSettings.Builder();
            var spawners = json.getAsJsonObject("spawners");
            if (spawners != null) {
                for (var entry : spawners.entrySet()) {
                    var category = mobCategory(entry.getKey());
                    if (category == null || !entry.getValue().isJsonArray()) continue;
                    for (var spawnElement : entry.getValue().getAsJsonArray()) {
                        if (!spawnElement.isJsonObject()) continue;
                        var spawn = spawnElement.getAsJsonObject();
                        var typeId = stringValue(spawn, "type", null);
                        if (typeId == null) continue;
                        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(typeId));
                        if (type == null) continue;
                        spawns.addSpawn(category, intValue(spawn, "weight", 1), new MobSpawnSettings.SpawnerData((EntityType<?>) type, intValue(spawn, "minCount", 1), intValue(spawn, "maxCount", 1)));
                    }
                }
            }

            var featuresJson = json.getAsJsonArray("features");
            var carversJson = json.getAsJsonArray("carvers");

            var builder = new Biome.BiomeBuilder()
                .hasPrecipitation(booleanValue(json, "hasPrecipitation", true))
                .temperature(floatValue(json, "temperature", 0.8f))
                .downfall(floatValue(json, "downfall", 0.4f))
                .temperatureAdjustment(temperatureModifier(stringValue(json, "temperatureModifier", "none")))
                .specialEffects(effects.build())
                .mobSpawnSettings(spawns.build())
                .generationSettings(buildGenerationSettings(featuresJson, carversJson));

            if (json.has("skyColor")) builder.setAttribute(EnvironmentAttributes.SKY_COLOR, colorValue(json, "skyColor", 0x78a7ff));
            if (json.has("fogColor")) builder.setAttribute(EnvironmentAttributes.FOG_COLOR, colorValue(json, "fogColor", 0xc0d8ff));
            if (json.has("waterFogColor")) builder.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, colorValue(json, "waterFogColor", 0x050533));
            if (json.has("cloudColor")) builder.setAttribute(EnvironmentAttributes.CLOUD_COLOR, colorValue(json, "cloudColor", 0xccffffff));
            if (json.has("ambientLightColor")) builder.setAttribute(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, colorValue(json, "ambientLightColor", 0x0a0a0a));
            if (json.has("canStartRaid")) builder.setAttribute(EnvironmentAttributes.CAN_START_RAID, booleanValue(json, "canStartRaid", true));
            if (json.has("waterEvaporates")) builder.setAttribute(EnvironmentAttributes.WATER_EVAPORATES, booleanValue(json, "waterEvaporates", false));
            if (json.has("respawnAnchorWorks")) builder.setAttribute(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, booleanValue(json, "respawnAnchorWorks", false));
            if (json.has("netherPortalSpawnsPiglins")) builder.setAttribute(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, booleanValue(json, "netherPortalSpawnsPiglins", false));
            if (json.has("fastLava")) builder.setAttribute(EnvironmentAttributes.FAST_LAVA, booleanValue(json, "fastLava", false));
            if (json.has("increasedFireBurnout")) builder.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, booleanValue(json, "increasedFireBurnout", false));
            if (json.has("snowGolemMelts")) builder.setAttribute(EnvironmentAttributes.SNOW_GOLEM_MELTS, booleanValue(json, "snowGolemMelts", false));
            if (json.has("monstersBurn")) builder.setAttribute(EnvironmentAttributes.MONSTERS_BURN, booleanValue(json, "monstersBurn", false));

            return builder.build();
        }

        private static BiomeGenerationSettings buildGenerationSettings(JsonArray featuresJson, JsonArray carversJson) {
            if ((featuresJson == null || featuresJson.isEmpty()) && (carversJson == null || carversJson.isEmpty())) {
                return BiomeGenerationSettings.EMPTY;
            }

            System.out.println("AkivCraft custom biome features/carvers requested but BiomeGenerationSettings has no public builder in MC 26.1.2; falling back to EMPTY");
            return BiomeGenerationSettings.EMPTY;
        }
    }

    private record BiomePlacement(
        String id,
        String source,
        Range temperature,
        Range humidity,
        Range continentalness,
        Range erosion,
        Range depth,
        Range weirdness,
        float offset
    ) {
        boolean matches(String presetId) {
            if ("all".equalsIgnoreCase(source)) return true;
            return switch (source.toLowerCase(java.util.Locale.ROOT)) {
                case "nether", "minecraft:nether" -> "minecraft:nether".equals(presetId);
                default -> "minecraft:overworld".equals(presetId);
            };
        }

        Climate.ParameterPoint parameterPoint() {
            return Climate.parameters(
                temperature.parameter(),
                humidity.parameter(),
                continentalness.parameter(),
                erosion.parameter(),
                depth.parameter(),
                weirdness.parameter(),
                offset
            );
        }
    }
}
