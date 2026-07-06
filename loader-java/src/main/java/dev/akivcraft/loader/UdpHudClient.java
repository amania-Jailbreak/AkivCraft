package dev.akivcraft.loader;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

public final class UdpHudClient {
    private static volatile List<HudBitmap> bitmaps = List.of();
    private static volatile long lastFrameTime;
    private static volatile boolean started;

    private UdpHudClient() {
    }

    public static void start(int port) {
        if (started) return;
        started = true;
        var thread = new Thread(() -> receive(port), "AkivCraft UDP HUD IPC");
        thread.setDaemon(true);
        thread.start();
    }

    public static List<HudBitmap> bitmaps() {
        return bitmaps;
    }

    public static boolean hasRecentFrame() {
        return System.currentTimeMillis() - lastFrameTime < 1000L && !bitmaps.isEmpty();
    }

    private static void receive(int port) {
        try (var socket = new DatagramSocket(port, InetAddress.getLoopbackAddress())) {
            System.out.printf("AkivCraft UDP HUD listening on 127.0.0.1:%d%n", port);
            var frames = new HashMap<Integer, Frame>();
            var buffer = new byte[1400];
            while (!Thread.currentThread().isInterrupted()) {
                var packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handle(packet, frames);
                frames.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().createdAt > 1000L);
            }
        } catch (IOException error) {
            System.err.printf("AkivCraft UDP HUD stopped: %s%n", error.getMessage());
        }
    }

    private static void handle(DatagramPacket packet, HashMap<Integer, Frame> frames) {
        var data = packet.getData();
        var length = packet.getLength();
        if (length < 18 || !"AKUD".equals(new String(data, 0, 4, StandardCharsets.US_ASCII))) return;

        var frameId = u32(data, 4);
        var chunkIndex = u16(data, 8);
        var chunkCount = u16(data, 10);
        var totalLength = u32(data, 12);
        var payloadOffset = 16;
        var payloadLength = length - payloadOffset;
        if (chunkCount <= 0 || chunkIndex >= chunkCount || totalLength <= 0 || payloadLength <= 0) return;

        var frame = frames.computeIfAbsent(frameId, ignored -> new Frame(chunkCount, totalLength));
        if (frame.totalLength != totalLength || frame.chunks.length != chunkCount) return;
        if (frame.chunks[chunkIndex] == null) {
            frame.chunks[chunkIndex] = new byte[payloadLength];
            System.arraycopy(data, payloadOffset, frame.chunks[chunkIndex], 0, payloadLength);
            frame.received++;
        }

        if (frame.received == frame.chunks.length) {
            var complete = new byte[frame.totalLength];
            var offset = 0;
            for (var chunk : frame.chunks) {
                if (chunk == null || offset + chunk.length > complete.length) return;
                System.arraycopy(chunk, 0, complete, offset, chunk.length);
                offset += chunk.length;
            }
            try {
                bitmaps = BinaryHudClient.parse(complete);
                lastFrameTime = System.currentTimeMillis();
            } catch (IOException ignored) {
            }
            frames.clear();
        }
    }

    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private static int u32(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
    }

    private static final class Frame {
        final byte[][] chunks;
        final int totalLength;
        final long createdAt = System.currentTimeMillis();
        int received;

        Frame(int chunkCount, int totalLength) {
            this.chunks = new byte[chunkCount][];
            this.totalLength = totalLength;
        }
    }
}
