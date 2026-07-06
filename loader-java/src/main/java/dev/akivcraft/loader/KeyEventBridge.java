package dev.akivcraft.loader;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class KeyEventBridge {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "AkivCraft key IPC");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile int port;

    private KeyEventBridge() {
    }

    public static void start(int ipcPort) {
        port = ipcPort;
    }

    public static void handle(int action, KeyEvent event) {
        if (port <= 0 || event == null) return;
        if (Minecraft.getInstance().screen != null) return;
        var key = event.key();
        var scancode = event.scancode();
        var modifiers = event.modifiers();
        if (StdioIpcBridge.enabled()) {
            StdioIpcBridge.sendKeyEvent(action, key, scancode, modifiers);
            return;
        }
        EXECUTOR.execute(() -> send(action, key, scancode, modifiers));
    }

    private static void send(int action, int key, int scancode, int modifiers) {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
             var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket.setSoTimeout(250);
            writer.printf("keyEvent\t%d\t%d\t%d\t%d%n", action, key, scancode, modifiers);
        } catch (IOException ignored) {
        }
    }

    public static void sendBinding(String id, boolean press) {
        if (port <= 0 || id == null || id.isBlank()) return;
        if (StdioIpcBridge.enabled()) {
            StdioIpcBridge.sendKeyBinding(id, press);
            return;
        }
        EXECUTOR.execute(() -> {
            try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                socket.setSoTimeout(250);
                writer.printf("keyBindingEvent\t%s\t%s%n", press ? "press" : "release", id);
            } catch (IOException ignored) {
            }
        });
    }
}
