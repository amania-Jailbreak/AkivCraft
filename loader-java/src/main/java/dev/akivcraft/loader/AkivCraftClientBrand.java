package dev.akivcraft.loader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AkivCraftClientBrand {
    private static volatile Path cachedPath;
    private static volatile long cachedModified;
    private static volatile String cachedBrand = "akivcraft";

    private AkivCraftClientBrand() {
    }

    public static String getClientModName() {
        try {
            var manifest = LoaderConfig.fromSystemProperties().modsDirectory().resolve("loaded-mods.json");
            var modified = Files.isRegularFile(manifest) ? Files.getLastModifiedTime(manifest).toMillis() : -1L;
            if (manifest.equals(cachedPath) && modified == cachedModified) return cachedBrand;

            cachedPath = manifest;
            cachedModified = modified;
            cachedBrand = formatBrand(countMods(manifest));
            return cachedBrand;
        } catch (Throwable ignored) {
            return cachedBrand;
        }
    }

    private static String formatBrand(int modCount) {
        return modCount >= 0 ? "akivcraft : " + modCount + " mods" : "akivcraft";
    }

    private static int countMods(Path manifest) {
        if (!Files.isRegularFile(manifest)) return -1;

        try {
            var json = Files.readString(manifest);
            var count = 0;
            var index = 0;
            while (true) {
                index = json.indexOf("\"id\"", index);
                if (index < 0) break;
                count++;
                index += 4;
            }
            return count;
        } catch (Exception ignored) {
            return -1;
        }
    }
}
