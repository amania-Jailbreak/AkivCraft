package dev.akivcraft.loader.via;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class ViaConfigStore {
    private static volatile boolean enabled = true;
    private static volatile int version = -2;
    private static volatile Path configFile;

    private ViaConfigStore() {
    }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    public static void init(Path homeDir) {
        configFile = homeDir.resolve("via").resolve("client.properties");
        if (!Files.isRegularFile(configFile)) return;

        try (var input = Files.newInputStream(configFile)) {
            var props = new Properties();
            props.load(input);
            version = Integer.parseInt(props.getProperty("version", "-2"));
            AkivVersionProvider.setClientSideVersion(version);
            System.out.printf("AkivCraft Via loaded selected version: %s%n", versionName(version));
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to load Via client config: %s%n", error.getMessage());
        }
    }

    public static int getVersion() { return version; }
    public static void setVersion(int v) {
        version = v;
        AkivVersionProvider.setClientSideVersion(v);
        save();
    }

    private static void save() {
        var file = configFile;
        if (file == null) return;

        try {
            Files.createDirectories(file.getParent());
            var props = new Properties();
            props.setProperty("version", Integer.toString(version));
            try (var output = Files.newOutputStream(file)) {
                props.store(output, "AkivCraft Via client settings");
            }
            System.out.printf("AkivCraft Via selected version: %s%n", versionName(version));
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to save Via client config: %s%n", error.getMessage());
        }
    }

    public static List<ProtocolVersion> availableVersions() {
        var result = new ArrayList<ProtocolVersion>();
        for (var v : ProtocolVersion.getProtocols()) {
            if (v.isKnown()) result.add(v);
        }
        return result;
    }

    public static String versionName(int id) {
        if (id == -2) return "AUTO";
        var v = ProtocolVersion.getProtocol(id);
        return v != null ? v.getName() : "Unknown (" + id + ")";
    }
}
