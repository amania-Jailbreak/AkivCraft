package dev.akivcraft.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class ModMetadataStore {
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");

    private ModMetadataStore() {
    }

    public static List<ModMetadata> discover() {
        var config = LoaderConfig.fromSystemProperties();
        var modsDirectory = config.modsDirectory();
        var loadedManifest = modsDirectory.resolve("loaded-mods.json");

        if (Files.isRegularFile(loadedManifest)) {
            var loaded = readLoadedManifest(loadedManifest);
            if (!loaded.isEmpty()) return loaded;
        }

        return readModDirectories(modsDirectory);
    }

    private static List<ModMetadata> readLoadedManifest(Path file) {
        try {
            var raw = Files.readString(file);
            var mods = new ArrayList<ModMetadata>();
            var matcher = Pattern.compile("\\{[^{}]*\"id\"[^{}]*}").matcher(raw);
            while (matcher.find()) {
                var object = matcher.group();
                var metadata = parseMetadata(object, true, file.getParent());
                mods.add(metadata);
            }
            mods.sort(Comparator.comparing(ModMetadata::name));
            return mods;
        } catch (IOException error) {
            return List.of();
        }
    }

    private static List<ModMetadata> readModDirectories(Path modsDirectory) {
        if (!Files.isDirectory(modsDirectory)) return List.of();

        try (var stream = Files.list(modsDirectory)) {
            return stream
                .filter(Files::isDirectory)
                .map(directory -> directory.resolve("mod.json"))
                .filter(Files::isRegularFile)
                .map(ModMetadataStore::readModJson)
                .sorted(Comparator.comparing(ModMetadata::name))
                .toList();
        } catch (IOException error) {
            return List.of();
        }
    }

    private static ModMetadata readModJson(Path file) {
        try {
            var modsDirectory = file.getParent() == null ? null : file.getParent().getParent();
            return parseMetadata(Files.readString(file), true, modsDirectory);
        } catch (IOException error) {
            var id = file.getParent() == null ? "unknown" : file.getParent().getFileName().toString();
            return new ModMetadata(id, id, "unknown", "Failed to read mod metadata", false, "");
        }
    }

    private static ModMetadata parseMetadata(String json, boolean enabledFallback) {
        return parseMetadata(json, enabledFallback, null);
    }

    private static ModMetadata parseMetadata(String json, boolean enabledFallback, Path modsDirectory) {
        var id = stringField(json, "id", "unknown");
        var name = stringField(json, "name", id);
        var version = stringField(json, "version", "unknown");
        var description = stringField(json, "description", "");
        var enabled = booleanField(json, "enabled", enabledFallback);
        var readme = readReadme(modsDirectory, id);
        return new ModMetadata(id, name, version, description, enabled, readme);
    }

    private static String readReadme(Path modsDirectory, String id) {
        if (modsDirectory == null || id == null || id.isBlank()) return "";

        var modDirectory = modsDirectory.resolve(id);
        var candidates = new String[] { "README.md", "readme.md", "Readme.md" };
        for (var candidate : candidates) {
            var file = modDirectory.resolve(candidate);
            if (!Files.isRegularFile(file)) continue;
            try {
                return Files.readString(file);
            } catch (IOException ignored) {
                return "";
            }
        }
        return "";
    }

    private static String stringField(String json, String field, String fallback) {
        var matcher = Pattern.compile(STRING_FIELD.pattern().formatted(Pattern.quote(field))).matcher(json);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static boolean booleanField(String json, String field, boolean fallback) {
        var matcher = Pattern.compile("\"%s\"\\s*:\\s*(true|false)".formatted(Pattern.quote(field))).matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }
}
