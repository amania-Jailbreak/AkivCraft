package dev.akivcraft.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Set;

public final class AkivCraftTransformingClassLoader extends ClassLoader {
    private final List<ClassFileTransformer> transformers;

    public AkivCraftTransformingClassLoader(ClassLoader parent, List<ClassFileTransformer> transformers, Set<String> transformTargets) {
        super(parent);
        this.transformers = transformers;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            var loaded = findLoadedClass(name);
            if (loaded == null) {
                if (shouldLoadChildFirst(name)) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loaded = super.loadClass(name, false);
                    }
                } else {
                    loaded = super.loadClass(name, false);
                }
            }
            if (resolve) resolveClass(loaded);
            return loaded;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        var path = name.replace('.', '/') + ".class";
        var bytes = readClassBytes(path);
        if (bytes == null) throw new ClassNotFoundException(name);

        bytes = transform(name, bytes);

        var packageName = packageName(name);
        if (packageName != null && getDefinedPackage(packageName) == null) {
            try {
                definePackage(packageName, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return defineClass(name, bytes, 0, bytes.length, (ProtectionDomain) null);
    }

    private byte[] transform(String name, byte[] bytes) {
        var internalName = name.replace('.', '/');
        var current = bytes;
        for (var transformer : transformers) {
            try {
                var transformed = transformer.transform(this, internalName, null, null, current);
                if (transformed != null) current = transformed;
            } catch (Throwable error) {
                System.err.printf("AkivCraft transformer %s failed for %s: %s%n", transformer.getClass().getSimpleName(), name, error.getMessage());
            }
        }
        return current;
    }

    private byte[] readClassBytes(String path) throws ClassNotFoundException {
        try (var input = getParent().getResourceAsStream(path)) {
            if (input == null) return null;
            var output = new ByteArrayOutputStream(Math.max(4096, input.available()));
            input.transferTo(output);
            return output.toByteArray();
        } catch (IOException error) {
            throw new ClassNotFoundException(path, error);
        }
    }

    /**
     * Child-first for all Minecraft/Mojang classes and our own helpers.
     * Parent-first for known third-party libraries that don't reference Minecraft.
     *
     * <p>This avoids split-brain: Minecraft classes like {@code BuiltInRegistries}
     * and {@code Bootstrap} must live in the same classloader, otherwise
     * bootstrap checks and registry initialization fail. Mojang library classes
     * (realms, authlib, DFU, brigadier) reference Minecraft types and must also
     * be in the child. Third-party libraries (LWJGL, Netty, Guava, etc.) never
     * reference Minecraft game classes and stay in the parent.
     */
    private static boolean shouldLoadChildFirst(String name) {
        if (name.equals("dev.akivcraft.loader.AkivCraftMain")) return false;
        if (name.equals("dev.akivcraft.loader.AkivCraftTransformingClassLoader")) return false;
        if (name.equals("dev.akivcraft.loader.AkivCraftTransformerSet")) return false;
        if (name.startsWith("dev.akivcraft.loader.")) return true;

        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.") || name.startsWith("com.sun.")) return false;
        if (name.startsWith("org.lwjgl.")) return false;
        if (name.startsWith("io.netty.")) return false;
        if (name.startsWith("org.apache.")) return false;
        if (name.startsWith("com.google.")) return false;
        if (name.startsWith("org.slf4j.")) return false;
        if (name.startsWith("it.unimi.")) return false;
        if (name.startsWith("net.java.dev.jna.")) return false;
        if (name.startsWith("net.sf.jopt.")) return false;
        if (name.startsWith("org.jcraft.")) return false;
        if (name.startsWith("org.joml.")) return false;
        if (name.startsWith("org.jspecify.")) return false;
        if (name.startsWith("com.azure.")) return false;
        if (name.startsWith("com.ibm.")) return false;
        if (name.startsWith("com.microsoft.")) return false;
        if (name.startsWith("commons.")) return false;
        if (name.startsWith("at.yawk.")) return false;
        if (name.startsWith("ca.weblite.")) return false;

        return true;
    }

    private static String packageName(String name) {
        var idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : null;
    }
}
