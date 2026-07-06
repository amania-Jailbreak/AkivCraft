package dev.akivcraft.loader;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import sun.misc.Unsafe;

public final class AkivCraftKeyMappings {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "AkivCraft key mapping IPC");
        thread.setDaemon(true);
        return thread;
    });
    private static final Pattern BINDING_PATTERN = Pattern.compile("\\{\\\"id\\\":\\\"([^\\\"]+)\\\",\\\"category\\\":\\\"([^\\\"]+)\\\",\\\"name\\\":\\\"([^\\\"]+)\\\",\\\"defaultKey\\\":(-?\\d+)");
    private static final Map<String, RegisteredBinding> bindings = new LinkedHashMap<>();
    private static final Map<String, KeyMapping.Category> categories = new LinkedHashMap<>();
    private static volatile int port;
    private static volatile long lastRefresh;
    private static volatile boolean refreshInFlight;
    private static final Unsafe UNSAFE = unsafe();

    private AkivCraftKeyMappings() {
    }

    public static void start(int ipcPort) {
        port = ipcPort;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || port <= 0) return;
        refreshIfNeeded(minecraft);
        dispatchEvents(minecraft);
    }

    private static void refreshIfNeeded(Minecraft minecraft) {
        var now = System.currentTimeMillis();
        if (refreshInFlight || now - lastRefresh < 1000L) return;
        refreshInFlight = true;
        lastRefresh = now;
        EXECUTOR.execute(() -> {
            try {
                var remote = fetchBindings();
                if (!remote.isEmpty()) {
                    minecraft.execute(() -> registerMissing(minecraft, remote));
                }
            } finally {
                refreshInFlight = false;
            }
        });
    }

    private static List<RemoteBinding> fetchBindings() {
        if (StdioIpcBridge.enabled()) return parseBindings(StdioIpcBridge.keybindingsJson());

        try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
             var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(250);
            writer.println("getKeybindings");
            var response = reader.readLine();
            if (response == null || response.isBlank()) return List.of();

            return parseBindings(response);
        } catch (IOException | RuntimeException ignored) {
            return List.of();
        }
    }

    private static List<RemoteBinding> parseBindings(String response) {
        var result = new ArrayList<RemoteBinding>();
        var matcher = BINDING_PATTERN.matcher(response);
        while (matcher.find()) {
            result.add(new RemoteBinding(
                unescape(matcher.group(1)),
                unescape(matcher.group(2)),
                unescape(matcher.group(3)),
                Integer.parseInt(matcher.group(4))
            ));
        }
        return result;
    }

    private static void registerMissing(Minecraft minecraft, List<RemoteBinding> remoteBindings) {
        var added = false;
        for (var remote : remoteBindings) {
            if (bindings.containsKey(remote.id())) continue;

            var mapping = new KeyMapping(
                keyName(remote.id()),
                InputConstants.Type.KEYSYM,
                remote.defaultKey(),
                category(remote.category())
            );
            bindings.put(remote.id(), new RegisteredBinding(remote.id(), mapping));
            added = true;
            System.out.printf("Registered Minecraft key mapping for AkivCraft: %s (%s)%n", remote.name(), remote.id());
        }

        if (!added) return;
        appendMappings(minecraft.options, bindings.values().stream().map(RegisteredBinding::mapping).toList());
        KeyMapping.resetMapping();
        try {
            minecraft.options.load();
            KeyMapping.resetMapping();
        } catch (Throwable error) {
            System.err.printf("AkivCraft failed to reload key mappings from options.txt: %s%n", error.getMessage());
        }
    }

    private static void appendMappings(Options options, List<KeyMapping> akivMappings) {
        try {
            var field = Options.class.getField("keyMappings");
            var existing = options.keyMappings;
            var merged = new ArrayList<KeyMapping>(List.of(existing));
            for (var mapping : akivMappings) {
                if (!merged.contains(mapping)) merged.add(mapping);
            }
            setField(field, options, merged.toArray(KeyMapping[]::new));
            System.out.printf("AkivCraft key mappings available in Controls: %d total%n", options.keyMappings.length);
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.printf("AkivCraft failed to add key mappings to options: %s%n", error.getMessage());
        }
    }

    private static void setField(Field field, Object owner, Object value) throws ReflectiveOperationException {
        field.setAccessible(true);
        if (UNSAFE != null) {
            UNSAFE.putObject(owner, UNSAFE.objectFieldOffset(field), value);
        } else {
            field.set(owner, value);
        }
    }

    private static void dispatchEvents(Minecraft minecraft) {
        if (minecraft.screen != null) {
            for (var binding : bindings.values()) {
                while (binding.mapping().consumeClick()) {
                    // Drain queued clicks from menus/text boxes so they do not fire after closing the screen.
                }
                binding.setWasDown(false);
            }
            return;
        }

        for (var binding : bindings.values()) {
            while (binding.mapping().consumeClick()) {
                KeyEventBridge.sendBinding(binding.id(), true);
            }

            var down = binding.mapping().isDown();
            if (binding.wasDown() && !down) {
                KeyEventBridge.sendBinding(binding.id(), false);
            }
            binding.setWasDown(down);
        }
    }

    private static KeyMapping.Category category(String value) {
        var path = safePath(value);
        return categories.computeIfAbsent(path, key -> KeyMapping.Category.register(Identifier.fromNamespaceAndPath("akivcraft", key)));
    }

    private static String safePath(String value) {
        var normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return normalized.isBlank() ? "misc" : normalized;
    }

    private static String keyName(String id) {
        return "key.akivcraft." + safePath(id).replace('/', '.');
    }

    private static Unsafe unsafe() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String unescape(String value) {
        return value.replace("\\\\", "\\").replace("\\\"", "\"");
    }

    private record RemoteBinding(String id, String category, String name, int defaultKey) {
    }

    private static final class RegisteredBinding {
        private final String id;
        private final KeyMapping mapping;
        private boolean wasDown;

        RegisteredBinding(String id, KeyMapping mapping) {
            this.id = id;
            this.mapping = mapping;
        }

        String id() {
            return id;
        }

        KeyMapping mapping() {
            return mapping;
        }

        boolean wasDown() {
            return wasDown;
        }

        void setWasDown(boolean wasDown) {
            this.wasDown = wasDown;
        }
    }
}
