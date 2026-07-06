package dev.akivcraft.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
            } else {
                writer.println("{\"error\":\"unknown request\"}");
            }
        } catch (IOException ignored) {
        }
    }
}
