package dev.akivcraft.loader;

import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatCapture {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "AkivCraft chat IPC");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile int port;

    private ChatCapture() {
    }

    public static void start(int ipcPort) {
        port = ipcPort;
    }

    public static void onSystemMessage(Component component) {
        send("system", component.getString());
    }

    public static void onPlayerMessage(Component component) {
        send("player", component.getString());
    }

    public static void onClientMessage(Component component) {
        send("client", component.getString());
    }

    private static void send(String type, String text) {
        if (port <= 0 || text == null || text.isBlank()) return;
        var sanitized = text.replace("\t", " ").replace("\n", " ").replace("\r", " ");

        if (StdioIpcBridge.enabled()) {
            StdioIpcBridge.sendChatMessage(type, sanitized);
            return;
        }

        EXECUTOR.execute(() -> {
            try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                socket.setSoTimeout(250);
                writer.printf(Locale.ROOT, "chatMessage\t%s\t%s%n", type, sanitized);
            } catch (IOException ignored) {
            }
        });
    }
}
