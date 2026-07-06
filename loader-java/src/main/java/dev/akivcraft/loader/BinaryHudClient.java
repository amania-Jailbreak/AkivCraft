package dev.akivcraft.loader;

import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class BinaryHudClient {
    private static volatile List<HudBitmap> bitmaps = List.of();
    private static volatile boolean started;

    private BinaryHudClient() {
    }

    public static void start(int port) {
        if (started) return;
        started = true;
        var thread = new Thread(() -> poll(port), "AkivCraft binary HUD IPC");
        thread.setDaemon(true);
        thread.start();
    }

    public static List<HudBitmap> bitmaps() {
        return bitmaps;
    }

    static void setBitmaps(List<HudBitmap> next) {
        bitmaps = List.copyOf(next);
    }

    static List<HudBitmap> parse(byte[] bytes) throws IOException {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return parse(input);
        }
    }

    private static void poll(int port) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                bitmaps = request(port);
            } catch (IOException | IllegalArgumentException ignored) {
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<HudBitmap> request(int port) throws IOException {
        try (var socket = new Socket(InetAddress.getLoopbackAddress(), port);
             var output = socket.getOutputStream();
             var input = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(250);
            output.write('B');
            output.flush();

            return parse(input);
        }
    }

    private static List<HudBitmap> parse(DataInputStream input) throws IOException {
        var magic = new byte[4];
        input.readFully(magic);
        if (!"AKBM".equals(new String(magic, StandardCharsets.US_ASCII))) return List.of();

        var version = input.readUnsignedByte();
        if (version != 1) return List.of();

        var count = input.readUnsignedShort();
        var next = new ArrayList<HudBitmap>(count);
        for (var i = 0; i < count; i++) {
            var idBytes = new byte[input.readUnsignedShort()];
            input.readFully(idBytes);
            var id = new String(idBytes, StandardCharsets.UTF_8);
            var x = input.readShort();
            var y = input.readShort();
            var width = input.readUnsignedShort();
            var height = input.readUnsignedShort();
            var scale = input.readUnsignedByte() / 10f;
            var palette = new int[input.readUnsignedShort()];
            for (var paletteIndex = 0; paletteIndex < palette.length; paletteIndex++) {
                palette[paletteIndex] = input.readInt();
            }
            var pixels = new byte[input.readInt()];
            input.readFully(pixels);
            if (pixels.length >= width * height) {
                next.add(new HudBitmap(id, x, y, width, height, scale <= 0 ? 1f : scale, palette, pixels));
            }
        }

        return List.copyOf(next);
    }
}
