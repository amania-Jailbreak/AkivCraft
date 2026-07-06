package dev.akivcraft.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

public final class StateIpcServer {
    private final int port;

    public StateIpcServer(int port) {
        this.port = port;
    }

    public boolean start() {
        var thread = new Thread(this::run, "AkivCraft State IPC");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private void run() {
        try (var server = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
            System.out.printf("AkivCraft state IPC listening on 127.0.0.1:%d%n", port);
            while (!Thread.currentThread().isInterrupted()) {
                handle(server.accept());
            }
        } catch (IOException error) {
            System.err.printf("AkivCraft state IPC stopped: %s%n", error.getMessage());
        }
    }

    private void handle(Socket socket) {
        try (socket;
             var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            var line = reader.readLine();
            if ("getState".equals(line)) {
                writer.println(MinecraftStateCapture.currentJson());
            } else if (line != null && line.startsWith("playerAction\t")) {
                PlayerActionHandler.handle(line);
                writer.println("OK");
            } else if (line != null && line.startsWith("getBlock ")) {
                var parts = line.split(" ");
                if (parts.length < 4) { writer.println("{\"error\":\"invalid request\"}"); return; }
                var x = Integer.parseInt(parts[1]);
                var y = Integer.parseInt(parts[2]);
                var z = Integer.parseInt(parts[3]);
                writer.println(queryBlockJson(x, y, z));
            } else if (line != null && line.startsWith("getBlocks ")) {
                var parts = line.split(" ");
                if (parts.length < 7) { writer.println("{\"error\":\"invalid request\"}"); return; }
                var x1 = Integer.parseInt(parts[1]);
                var y1 = Integer.parseInt(parts[2]);
                var z1 = Integer.parseInt(parts[3]);
                var x2 = Integer.parseInt(parts[4]);
                var y2 = Integer.parseInt(parts[5]);
                var z2 = Integer.parseInt(parts[6]);
                writer.println(queryBlocksJson(x1, y1, z1, x2, y2, z2));
            } else {
                writer.println("{\"error\":\"unknown request\"}");
            }
        } catch (IOException ignored) {
        }
    }

    public static String queryBlockJson(int x, int y, int z) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return "{\"error\":\"no level\"}";
        var state = level.getBlockState(new BlockPos(x, y, z));
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return "{\"blockId\":\"" + escape(id) + "\"}";
    }

    public static String queryBlocksJson(int x1, int y1, int z1, int x2, int y2, int z2) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return "{\"error\":\"no level\"}";

        var minX = Math.min(x1, x2);
        var maxX = Math.max(x1, x2);
        var minY = Math.min(y1, y2);
        var maxY = Math.max(y1, y2);
        var minZ = Math.min(z1, z2);
        var maxZ = Math.max(z1, z2);

        var dx = maxX - minX + 1;
        var dy = maxY - minY + 1;
        var dz = maxZ - minZ + 1;
        if (dx * dy * dz > 4096) return "{\"error\":\"region too large (max 4096 blocks)\"}";

        var sb = new StringBuilder("{\"blocks\":[");
        var pos = new BlockPos.MutableBlockPos();
        var first = true;
        for (var y = minY; y <= maxY; y++) {
            for (var z = minZ; z <= maxZ; z++) {
                for (var x = minX; x <= maxX; x++) {
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!first) sb.append(',');
                    first = false;
                    var id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    sb.append("{\"x\":").append(x).append(",\"y\":").append(y).append(",\"z\":").append(z).append(",\"id\":\"").append(escape(id)).append("\"}");
                }
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
