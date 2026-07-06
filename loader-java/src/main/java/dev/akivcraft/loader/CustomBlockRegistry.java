package dev.akivcraft.loader;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CustomBlockRegistry {
    private static final List<String> unsupportedProperties = new ArrayList<>();

    private CustomBlockRegistry() {
    }

    @SuppressWarnings("unchecked")
    public static void registerFromFile(Path file, Object registryObject) {
        var registry = (Registry<Block>) registryObject;
        if (file == null || !Files.isRegularFile(file)) {
            AkivCraftLoadingLog.warn("Custom blocks file not found: " + file);
            return;
        }

        try {
            var json = Files.readString(file);
            AkivCraftLoadingLog.info("Reading custom blocks from " + file.getFileName());
            registerBlocksFromJson(json, registry);
        } catch (Exception error) {
            AkivCraftLoadingLog.error("Failed to read custom blocks from " + file + ": " + error.getMessage());
        }
    }

    private static void registerBlocksFromJson(String json, Registry<Block> registry) {
        var blocksMarker = "\"blocks\"";
        var markerIdx = json.indexOf(blocksMarker);
        if (markerIdx < 0) return;

        var arrStart = json.indexOf('[', markerIdx);
        if (arrStart < 0) return;
        var arrEnd = findMatching(json, arrStart, '[', ']');
        if (arrEnd < 0) return;

        var blocksArray = json.substring(arrStart + 1, arrEnd);
        for (var block : extractObjects(blocksArray)) {
            var id = stringField(block, "\"id\":", "\"");
            if (id != null && !id.isBlank()) {
                registerBlock(id, block, registry);
            }
        }
    }

    private static void registerBlock(String id, String block, Registry<Block> registry) {
        try {
            var identifier = Identifier.parse(id);
            if (registry.containsKey(identifier)) {
                AkivCraftLoadingLog.warn("Custom block " + id + " is already registered, skipping");
                return;
            }

            var key = ResourceKey.create(Registries.BLOCK, identifier);
            var properties = BlockBehaviour.Properties.of()
                .setId(key)
                .mapColor(mapColor(stringField(block, "\"material\":", "\"")))
                .sound(soundType(stringField(block, "\"material\":", "\"")));

            applyFloatPair(block, "\"hardness\":", "\"explosionResistance\":", properties);
            applyLightLevel(block, properties);
            applyBooleanFlag(block, "\"requiresTool\":", properties, BlockBehaviour.Properties::requiresCorrectToolForDrops);
            applyBooleanFlag(block, "\"instabreak\":", properties, BlockBehaviour.Properties::instabreak);
            applyBooleanFlag(block, "\"noCollision\":", properties, BlockBehaviour.Properties::noCollision);
            applyBooleanFlag(block, "\"noOcclusion\":", properties, BlockBehaviour.Properties::noOcclusion);
            applyBooleanFlag(block, "\"air\":", properties, BlockBehaviour.Properties::air);

            if (block.contains("\"liquid\":")) {
                warnUnsupported("liquid");
            }

            var friction = floatField(block, "\"friction\":", Float.NaN);
            if (!Float.isNaN(friction)) properties.friction(friction);

            var speedFactor = floatField(block, "\"speedFactor\":", Float.NaN);
            if (!Float.isNaN(speedFactor)) properties.speedFactor(speedFactor);

            var jumpFactor = floatField(block, "\"jumpFactor\":", Float.NaN);
            if (!Float.isNaN(jumpFactor)) properties.jumpFactor(jumpFactor);

            var name = stringField(block, "\"name\":", "\"");
            if (name != null && !name.isBlank()) {
                properties.overrideDescription("block." + identifier.getNamespace() + "." + identifier.getPath().replace('/', '.'));
            }

            var registered = Registry.register(registry, key, new Block(properties));
            AkivCraftLoadingLog.info("Registered custom block " + id + " (" + registry.getKey(registered) + ")");
        } catch (Throwable error) {
            AkivCraftLoadingLog.error("Failed custom block " + id + ": " + error.getMessage());
            error.printStackTrace();
        }
    }

    private static void applyFloatPair(String source, String hardnessKey, String resistanceKey, BlockBehaviour.Properties properties) {
        var hardness = floatField(source, hardnessKey, Float.NaN);
        var resistance = floatField(source, resistanceKey, Float.NaN);

        if (!Float.isNaN(hardness) && !Float.isNaN(resistance)) {
            properties.strength(hardness, resistance);
        } else if (!Float.isNaN(hardness)) {
            properties.strength(hardness);
        } else if (!Float.isNaN(resistance)) {
            properties.explosionResistance(resistance);
        }
    }

    private static void applyLightLevel(String source, BlockBehaviour.Properties properties) {
        var level = intField(source, "\"lightLevel\":", Integer.MIN_VALUE);
        if (level != Integer.MIN_VALUE) {
            properties.lightLevel(state -> Math.max(0, Math.min(15, level)));
        }
    }

    private static void applyBooleanFlag(String source, String key, BlockBehaviour.Properties properties, java.util.function.Consumer<BlockBehaviour.Properties> setter) {
        var idx = source.indexOf(key);
        if (idx < 0) return;
        var tail = source.substring(idx + key.length());
        tail = tail.replaceFirst("^\\s*:", "").trim();
        if (tail.startsWith("true")) {
            setter.accept(properties);
        }
    }

    private static MapColor mapColor(String material) {
        return switch ((material == null ? "stone" : material).toLowerCase(Locale.ROOT)) {
            case "grass", "plant" -> MapColor.PLANT;
            case "dirt", "clay" -> MapColor.DIRT;
            case "wood" -> MapColor.WOOD;
            case "stone", "rock", "metal" -> MapColor.STONE;
            case "iron" -> MapColor.METAL;
            case "sand" -> MapColor.SAND;
            case "wool", "cloth" -> MapColor.WOOL;
            case "fire" -> MapColor.FIRE;
            case "ice" -> MapColor.ICE;
            case "water" -> MapColor.WATER;
            case "snow" -> MapColor.SNOW;
            default -> MapColor.STONE;
        };
    }

    private static SoundType soundType(String material) {
        return switch ((material == null ? "stone" : material).toLowerCase(Locale.ROOT)) {
            case "wood" -> SoundType.WOOD;
            case "grass", "plant", "crop" -> SoundType.GRASS;
            case "dirt", "clay" -> SoundType.GRAVEL;
            case "gravel" -> SoundType.GRAVEL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "glass" -> SoundType.GLASS;
            case "metal", "iron" -> SoundType.METAL;
            case "wool", "cloth" -> SoundType.WOOL;
            case "slime" -> SoundType.SLIME_BLOCK;
            case "honey" -> SoundType.HONEY_BLOCK;
            case "stone", "rock" -> SoundType.STONE;
            default -> SoundType.STONE;
        };
    }

    private static String stringField(String source, String prefix, String suffix) {
        var start = source.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        if (start >= source.length() || source.charAt(start) != '"') return null;
        start++;
        var end = source.indexOf(suffix, start);
        if (end < 0) return null;
        return source.substring(start, end);
    }

    private static int intField(String source, String prefix, int fallback) {
        var start = source.indexOf(prefix);
        if (start < 0) return fallback;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        var end = start;
        while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(source.substring(start, end).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float floatField(String source, String prefix, float fallback) {
        var start = source.indexOf(prefix);
        if (start < 0) return fallback;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        var end = start;
        while (end < source.length() && (Character.isDigit(source.charAt(end)) || source.charAt(end) == '-' || source.charAt(end) == '.')) end++;
        try {
            return Float.parseFloat(source.substring(start, end).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> extractObjects(String source) {
        var objects = new ArrayList<String>();
        var depth = 0;
        var start = -1;
        var inString = false;

        for (var i = 0; i < source.length(); i++) {
            var c = source.charAt(i);
            if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        objects.add(source.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }
        return objects;
    }

    private static int findMatching(String json, int openIdx, char open, char close) {
        var depth = 0;
        var inString = false;
        for (var i = openIdx; i < json.length(); i++) {
            var c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static void warnUnsupported(String property) {
        if (!unsupportedProperties.contains(property)) {
            unsupportedProperties.add(property);
            AkivCraftLoadingLog.warn("Custom block property '" + property + "' is not supported in this loader version and will be skipped");
        }
    }
}
