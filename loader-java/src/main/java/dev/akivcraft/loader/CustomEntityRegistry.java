package dev.akivcraft.loader;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.pig.Pig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers custom entity types from {@code loaded-entities.json} into the
 * {@code BuiltInRegistries.ENTITY_TYPE} registry at freeze time.
 *
 * <p>The registration is driven by {@link FreezeTransformer}, which calls
 * {@link #registerFromFile(Path, Object)} when the entity type registry is
 * about to be frozen.
 *
 * <p>Current limitations:
 * <ul>
 *   <li>Custom entity types reuse the vanilla {@link Pig} factory. They will
 *       behave and render exactly like pigs on the client until a custom
 *       renderer and entity class are added.</li>
 *   <li>No client renderer is registered; clients that do not run AkivCraft
 *       will see the default pig renderer and may need a matching resource
 *       pack / mod to render the intended model.</li>
 *   <li>Summonable is inferred from the JSON field; the entity can be spawned
 *       with {@code /summon akivcraft.mymod:ruby_golem}.</li>
 * </ul>
 */
public final class CustomEntityRegistry {
    private static final String ENTITIES_MARKER = "\"entities\"";
    private static int entityRegistrationFailures;

    private CustomEntityRegistry() {
    }

    /**
     * Reads {@code loaded-entities.json} and registers every configured entity
     * into the supplied entity-type registry.
     *
     * @param file           path to the JSON file, may be absent
     * @param registryObject the {@link Registry} for {@link EntityType}s, cast
     *                       from the raw object passed by the freeze hook
     */
    @SuppressWarnings("unchecked")
    public static void registerFromFile(Path file, Object registryObject) {
        var registry = (Registry<EntityType<?>>) registryObject;
        entityRegistrationFailures = 0;

        if (file == null || !Files.isRegularFile(file)) {
            AkivCraftLoadingLog.info("Custom entities file not found: " + file);
            return;
        }

        try {
            var json = Files.readString(file);
            AkivCraftLoadingLog.info("Reading custom entities from " + file.getFileName());
            registerEntitiesFromJson(json, registry);
            if (entityRegistrationFailures > 0) {
                throw new AkivCraftStartupException("Failed to register " + entityRegistrationFailures + " custom entities");
            }
        } catch (Exception error) {
            if (error instanceof AkivCraftStartupException startupError) throw startupError;
            AkivCraftLoadingLog.error("Failed to read custom entities: " + error.getMessage());
            throw new AkivCraftStartupException("Failed to read custom entities from " + file, error);
        }
    }

    private static void registerEntitiesFromJson(String json, Registry<EntityType<?>> registry) {
        var markerIdx = json.indexOf(ENTITIES_MARKER);
        if (markerIdx < 0) return;

        var arrStart = json.indexOf('[', markerIdx);
        if (arrStart < 0) return;
        var arrEnd = findMatching(json, arrStart, '[', ']');
        if (arrEnd < 0) return;

        var entitiesArray = json.substring(arrStart + 1, arrEnd);
        for (var block : extractObjects(entitiesArray)) {
            var id = stringField(block, "\"id\":", "\"");
            if (id != null && !id.isBlank()) {
                registerEntity(id, block, registry);
            }
        }
    }

    private static void registerEntity(String id, String block, Registry<EntityType<?>> registry) {
        try {
            var identifier = Identifier.parse(id);
            if (registry.containsKey(identifier)) {
                AkivCraftLoadingLog.warn("Skipping entity registration, already registered: " + id);
                return;
            }

            var name = stringField(block, "\"name\":", "\"");
            var width = floatField(block, "\"width\":", 1.0f);
            var height = floatField(block, "\"height\":", 1.0f);
            var fireImmune = booleanField(block, "\"fireImmune\"");
            var summonable = booleanField(block, "\"summonable\"");
            var trackingRange = intField(block, "\"trackingRange\":", 10);
            var updateInterval = intField(block, "\"updateInterval\":", 3);
            var clientTrackingRange = intField(block, "\"clientTrackingRange\":", 10);

            var key = ResourceKey.create(Registries.ENTITY_TYPE, identifier);

            // Build the entity type using a vanilla pig factory. This is a
            // pragmatic placeholder: the entity will spawn and behave like a
            // pig until AkivCraft ships custom entity classes and renderers.
            var builder = EntityType.Builder.<Pig>of(Pig::new, MobCategory.CREATURE)
                .sized(width, height)
                .clientTrackingRange(clientTrackingRange)
                .updateInterval(updateInterval);

            if (fireImmune) builder.fireImmune();
            if (summonable) builder = builder; // "canSummon" is the default when not calling noSummon()
            else builder.noSummon();

            var entityType = builder.build(key);
            Registry.register(registry, key, entityType);

            AkivCraftLoadingLog.info("Registered custom entity " + id
                + " (" + (name != null ? name : identifier.getPath()) + ")");
        } catch (Throwable error) {
            entityRegistrationFailures++;
            AkivCraftLoadingLog.error("Failed entity " + id + ": " + error.getMessage());
            error.printStackTrace();
        }
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

    private static boolean booleanField(String source, String prefix) {
        var start = source.indexOf(prefix);
        if (start < 0) return false;
        start += prefix.length();
        while (start < source.length() && source.charAt(start) == ' ') start++;
        return start + 4 <= source.length()
            && source.substring(start, start + 4).equalsIgnoreCase("true");
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
}
