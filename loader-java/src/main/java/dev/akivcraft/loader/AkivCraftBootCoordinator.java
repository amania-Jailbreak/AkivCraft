package dev.akivcraft.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AkivCraftBootCoordinator {
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;
    private static volatile boolean prepared;
    private static volatile boolean started;
    private static volatile Throwable failure;
    private static volatile Thread prepareThread;
    private static volatile NodeRuntimeBridge bridge;

    private AkivCraftBootCoordinator() {
    }

    public static void startAsync() {
        if (started || prepared) return;
        synchronized (AkivCraftBootCoordinator.class) {
            if (started || prepared) return;
            started = true;
            prepareThread = new Thread(() -> {
                try {
                    prepareOrThrow();
                } catch (Throwable error) {
                    failure = error;
                }
            }, "AkivCraft Prepare");
            prepareThread.setDaemon(false);
            prepareThread.start();
        }
    }

    public static void awaitPreparedOrThrow() {
        startAsync();
        var thread = prepareThread;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(Long.getLong("akivcraft.startupTimeoutMillis", DEFAULT_TIMEOUT_MILLIS));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AkivCraftStartupException("Interrupted while waiting for AkivCraft preparation", error);
            }
        }
        if (failure != null) {
            if (failure instanceof RuntimeException runtimeException) throw runtimeException;
            throw new AkivCraftStartupException("AkivCraft preparation failed", failure);
        }
        if (!prepared) throw new AkivCraftStartupException("Timed out waiting for AkivCraft preparation");
    }

    public static void prepareOrThrow() {
        if (prepared) return;

        synchronized (AkivCraftBootCoordinator.class) {
            if (prepared) return;

            var config = LoaderConfig.fromSystemProperties();
            var timeoutMillis = Long.getLong("akivcraft.startupTimeoutMillis", DEFAULT_TIMEOUT_MILLIS);

            try {
                AkivCraftLoadingLog.stage("Preparing AkivCraft loader");
                resetGeneratedState(config);

                AkivCraftLoadingLog.stage("Initializing ViaVersion");
                dev.akivcraft.loader.via.ViaBootstrap.init(config.akivcraftHome());

                AkivCraftLoadingLog.stage("Starting Node runtime");
                bridge = new NodeRuntimeBridge(config);
                if (!bridge.start()) {
                    fail("Node runtime failed to start: " + config.nodeRuntimeEntry());
                }
                AkivCraftLoadingLog.info("Node runtime started");

                AkivCraftLoadingLog.stage("Waiting for AkivCraft manifests");
                waitForGeneratedState(config, timeoutMillis);

                prepared = true;
                AkivCraftLoadingLog.stage("AkivCraft loader prepared");
            } catch (AkivCraftStartupException error) {
                throw error;
            } catch (Throwable error) {
                fail("AkivCraft startup failed: " + error.getMessage(), error);
            }
        }
    }

    public static boolean isPrepared() {
        return prepared;
    }

    private static void resetGeneratedState(LoaderConfig config) throws IOException {
        Files.createDirectories(config.modsDirectory());
        deleteIfExists(config.modsDirectory().resolve("loaded-items.json"));
        deleteIfExists(config.modsDirectory().resolve("loaded-creative-tabs.json"));
        deleteIfExists(config.modsDirectory().resolve("loaded-biomes.json"));
        deleteIfExists(config.modsDirectory().resolve("loaded-mods.json"));
        deleteTree(config.akivcraftHome().resolve("generated-resourcepacks"));
    }

    private static void waitForGeneratedState(LoaderConfig config, long timeoutMillis) {
        var requiredFiles = List.of(
            config.modsDirectory().resolve("loaded-items.json"),
            config.modsDirectory().resolve("loaded-creative-tabs.json"),
            config.modsDirectory().resolve("loaded-biomes.json"),
            config.modsDirectory().resolve("loaded-mods.json")
        );
        var packsDir = config.akivcraftHome().resolve("generated-resourcepacks");
        var deadline = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() <= deadline) {
            if (bridge != null && !bridge.isRunning()) {
                fail("Node runtime exited before AkivCraft manifests were generated");
            }

            var ready = true;
            for (var file : requiredFiles) {
                if (!Files.isRegularFile(file)) {
                    ready = false;
                    break;
                }
            }

            if (ready && Files.isDirectory(packsDir)) return;

            try {
                Thread.sleep(100);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for AkivCraft manifests", error);
            }
        }

        var missing = new StringBuilder();
        for (var file : requiredFiles) {
            if (!Files.isRegularFile(file)) missing.append('\n').append("- ").append(file.toAbsolutePath());
        }
        if (!Files.isDirectory(packsDir)) missing.append('\n').append("- ").append(packsDir.toAbsolutePath());
        fail("AkivCraft startup timed out waiting for generated files:" + missing);
    }

    private static void deleteIfExists(Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            var entries = stream.sorted((left, right) -> right.getNameCount() - left.getNameCount()).toList();
            for (var entry : entries) Files.deleteIfExists(entry);
        }
    }

    private static void fail(String message) {
        AkivCraftLoadingLog.error(message);
        throw new AkivCraftStartupException(message);
    }

    private static void fail(String message, Throwable cause) {
        AkivCraftLoadingLog.error(message);
        throw new AkivCraftStartupException(message, cause);
    }
}
