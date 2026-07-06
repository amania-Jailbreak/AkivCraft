package dev.akivcraft.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class NodeHudClient {
    private static volatile List<HudItem> items = List.of();
    private static volatile boolean started;

    private NodeHudClient() {
    }

    public static void start(int port) {
        if (started) return;
        started = true;
        var thread = new Thread(() -> poll(port), "AkivCraft HUD IPC");
        thread.setDaemon(true);
        thread.start();
    }

    public static List<HudItem> items() {
        return items;
    }

    static void setItems(List<HudItem> next) {
        items = List.copyOf(next);
    }

    private static void poll(int port) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                items = request(port);
            } catch (IOException | IllegalArgumentException ignored) {
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<HudItem> request(int port) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket.setSoTimeout(250);
            writer.println("getHud");

            var lines = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (".".equals(line)) break;
                lines.add(line);
            }
            return parseLines(lines);
        }
    }

    static List<HudItem> parseLines(List<String> lines) {
        var next = new ArrayList<HudItem>();
        for (var line : lines) {
            var parts = line.split("\\t", -1);
            if (parts.length < 10) continue;
            next.add(new HudItem(
                parts[0],
                decode(parts[1]),
                decode(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5]),
                Integer.parseInt(parts[6]),
                (int) Long.parseLong(parts[7]),
                (int) Long.parseLong(parts[8]),
                "1".equals(parts[9])
            ));
        }
        return List.copyOf(next);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
