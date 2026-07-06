package dev.akivcraft.loader;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BlockEventIpc {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "AkivCraft block-event IPC");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile int port;

    private BlockEventIpc() {
    }

    public static void start(int ipcPort) {
        port = ipcPort;
    }

    public static void send(String json) {
        if (port <= 0 || json == null || json.isBlank()) return;
        if (StdioIpcBridge.enabled()) {
            StdioIpcBridge.sendBlockEvent(json);
            return;
        }

        var payload = json.replace("\n", " ").replace("\r", " ");
        EXECUTOR.execute(() -> {
            try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                socket.setSoTimeout(250);
                writer.printf(Locale.ROOT, "blockEvent\t%s%n", payload);
            } catch (IOException ignored) {
            }
        });
    }
}
