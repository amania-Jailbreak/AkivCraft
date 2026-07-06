package dev.akivcraft.loader;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ItemUseIpc {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "AkivCraft item-use IPC");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile int port;

    private ItemUseIpc() {
    }

    public static void start(int ipcPort) {
        port = ipcPort;
    }

    public static void sendItemUse(
        String itemId, String playerName,
        double x, double y, double z, String event,
        double lookX, double lookY, double lookZ,
        boolean rayHit, String hitX, String hitY, String hitZ
    ) {
        if (port <= 0 || itemId == null || itemId.isBlank()) return;
        var ev = event != null ? event : "use";
        if (StdioIpcBridge.enabled()) {
            StdioIpcBridge.sendItemUse(itemId, playerName, x, y, z, ev, lookX, lookY, lookZ, rayHit, hitX, hitY, hitZ);
            return;
        }
        EXECUTOR.execute(() -> {
            try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
                 var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
                socket.setSoTimeout(250);
                writer.printf(Locale.ROOT,
                    "itemUse\t%s\t%s\t%.3f\t%.3f\t%.3f\t%s\t%.4f\t%.4f\t%.4f\t%d\t%s\t%s\t%s%n",
                    itemId, playerName, x, y, z, ev,
                    lookX, lookY, lookZ,
                    rayHit ? 1 : 0, hitX, hitY, hitZ
                );
            } catch (IOException ignored) {
            }
        });
    }
}
