package dev.akivcraft.loader.shader;

import dev.akivcraft.loader.LoaderConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipFile;

public final class AkivShaderConfig {
    private static final String OFF = "OFF";
    private static volatile AkivShaderPack selected;
    private static volatile boolean loaded;

    private AkivShaderConfig() {
    }

    public static AkivShaderPack selectedPack() {
        if (!loaded) load();
        return selected;
    }

    public static List<AkivShaderPack> discover() {
        var root = shaderpacksDirectory();
        if (!Files.isDirectory(root)) return List.of();

        var packs = new ArrayList<AkivShaderPack>();
        try (var entries = Files.list(root)) {
            entries
                .map(AkivShaderConfig::packAt)
                .filter(pack -> pack != null)
                .sorted(Comparator.comparing(AkivShaderPack::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(packs::add);
        } catch (IOException error) {
            System.err.printf("AkivCraft shaderpack discovery failed: %s%n", error.getMessage());
        }

        return List.copyOf(packs);
    }

    private static synchronized void load() {
        if (loaded) return;

        var explicit = System.getProperty("akivcraft.shaderpack");
        var name = explicit != null ? explicit.trim() : configuredName();
        if (name == null || name.isBlank() || OFF.equalsIgnoreCase(name)) {
            System.out.println("AkivCraft shaderpack disabled: set .akivcraft/shaders/client.properties selected=<pack> or -Dakivcraft.shaderpack=<pack>");
            loaded = true;
            return;
        }

        var root = shaderpacksDirectory().toAbsolutePath().normalize();
        var pack = packAt(root.resolve(name).normalize());
        if (pack == null && !name.endsWith(".zip")) pack = packAt(root.resolve(name + ".zip").normalize());
        if (pack == null) {
            for (var candidate : discover()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    pack = candidate;
                    break;
                }
            }
        }

        if (pack == null || !pack.path().startsWith(root)) {
            System.err.printf("AkivCraft shaderpack '%s' not found at %s%n", name, root);
            loaded = true;
            return;
        }

        selected = pack;
        loaded = true;
        System.out.printf("AkivCraft selected shaderpack: %s%n", pack.name());
    }

    private static String configuredName() {
        var file = configFile();
        if (!Files.isRegularFile(file)) return null;

        var properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
            return properties.getProperty("selected", OFF).trim();
        } catch (IOException error) {
            System.err.printf("AkivCraft shader config load failed: %s%n", error.getMessage());
            return null;
        }
    }

    private static AkivShaderPack packAt(Path path) {
        var normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            if (!hasAnyShader(normalized)) return null;
            var properties = readDirectoryShaderProperties(normalized);
            return new AkivShaderPack(normalized.getFileName().toString(), normalized, false, properties);
        }

        if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".zip")) {
            if (!hasZipShader(normalized)) return null;
            var properties = readZipShaderProperties(normalized);
            var filename = normalized.getFileName().toString();
            var name = filename.substring(0, filename.length() - ".zip".length());
            return new AkivShaderPack(name, normalized, true, properties);
        }

        return null;
    }

    private static boolean hasAnyShader(Path directory) {
        var shaders = directory.resolve("shaders");
        if (!Files.isDirectory(shaders)) return false;
        try (var files = Files.walk(shaders, 2)) {
            return files.anyMatch(p -> {
                var name = p.getFileName().toString();
                return name.endsWith(".fsh") && !name.startsWith("._");
            });
        } catch (IOException error) {
            return false;
        }
    }

    private static boolean hasZipShader(Path zip) {
        try (var file = new ZipFile(zip.toFile())) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                var name = entries.nextElement().getName();
                if (name.startsWith("shaders/") && name.endsWith(".fsh") && !name.contains("/._")) return true;
            }
        } catch (IOException error) {
            return false;
        }
        return false;
    }

    private static Properties readDirectoryShaderProperties(Path directory) {
        var properties = new Properties();
        var file = directory.resolve("shaders").resolve("shaders.properties");
        if (!Files.isRegularFile(file)) return properties;

        try (var input = Files.newInputStream(file)) {
            properties.load(input);
            System.out.printf("AkivCraft loaded shaders.properties from %s%n", directory.getFileName());
        } catch (IOException error) {
            System.err.printf("AkivCraft failed to read shaders.properties from %s: %s%n", directory.getFileName(), error.getMessage());
        }
        return properties;
    }

    private static Properties readZipShaderProperties(Path zip) {
        var properties = new Properties();
        try (var file = new ZipFile(zip.toFile())) {
            var entry = file.getEntry("shaders/shaders.properties");
            if (entry == null) return properties;
            try (var input = file.getInputStream(entry)) {
                properties.load(input);
                System.out.printf("AkivCraft loaded shaders.properties from %s%n", zip.getFileName());
            }
        } catch (IOException error) {
            System.err.printf("AkivCraft failed to read shaders.properties from %s: %s%n", zip.getFileName(), error.getMessage());
        }
        return properties;
    }

    private static Path shaderpacksDirectory() {
        return LoaderConfig.fromSystemProperties().akivcraftHome().resolve("shaderpacks");
    }

    private static Path configFile() {
        return LoaderConfig.fromSystemProperties().akivcraftHome().resolve("shaders").resolve("client.properties");
    }

    public record ShaderPass(String name, String fragmentPath, String vertexPath) {}

    public record AkivShaderPack(String name, Path path, boolean zip, Properties shaderProperties) {
        private static final List<String> PASS_ORDER = buildPassOrder();

        private static List<String> buildPassOrder() {
            var list = new ArrayList<String>();
            list.add("deferred");
            for (int i = 1; i < 20; i++) list.add("deferred" + i);
            list.add("composite");
            for (int i = 1; i < 20; i++) list.add("composite" + i);
            list.add("final");
            return List.copyOf(list);
        }

        public List<ShaderPass> passes(String dimension) {
            var result = new ArrayList<ShaderPass>();
            for (var passName : PASS_ORDER) {
                var fsh = findPassFile(dimension, passName);
                if (fsh == null) continue;

                var vsh = fsh.substring(0, fsh.length() - ".fsh".length()) + ".vsh";
                var hasVsh = false;
                try { hasVsh = exists(vsh); } catch (IOException ignored) { }

                result.add(new ShaderPass(passName, fsh, hasVsh ? vsh : null));
            }
            return result;
        }

        private String findPassFile(String dimension, String passName) {
            var dimPath = "shaders/" + dimension + "/" + passName + ".fsh";
            try { if (exists(dimPath)) return dimPath; } catch (IOException ignored) { }
            var rootPath = "shaders/" + passName + ".fsh";
            try { if (exists(rootPath)) return rootPath; } catch (IOException ignored) { }
            return null;
        }

        public long timestamp() throws IOException {
            return Files.getLastModifiedTime(path).toMillis();
        }

        private boolean exists(String relativePath) throws IOException {
            var normalized = normalizeShaderPath(relativePath);
            if (!zip) return Files.isRegularFile(path.resolve(normalized));
            try (var file = new ZipFile(path.toFile())) {
                return file.getEntry(normalized) != null;
            }
        }

        public String readText(String relativePath) throws IOException {
            var normalized = normalizeShaderPath(relativePath);
            if (!zip) return Files.readString(path.resolve(normalized));
            try (var file = new ZipFile(path.toFile())) {
                var entry = file.getEntry(normalized);
                if (entry == null) throw new IOException("missing shaderpack entry " + normalized);
                try (var input = file.getInputStream(entry)) {
                    return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        private static String normalizeShaderPath(String path) {
            var normalized = Path.of(path).normalize().toString().replace('\\', '/');
            if (normalized.startsWith("../") || normalized.equals("..") || normalized.startsWith("/")) {
                throw new IllegalArgumentException("invalid shader include path: " + path);
            }
            return normalized;
        }
    }
}
