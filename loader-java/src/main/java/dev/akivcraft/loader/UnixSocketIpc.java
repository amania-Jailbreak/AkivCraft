package dev.akivcraft.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

public final class UnixSocketIpc {
    private static final Pattern TYPE_PATTERN = Pattern.compile("\"type\":\"([^\"]+)\"");
    private static final Pattern DATA_PATTERN = Pattern.compile("\"data\":\"([^\"]*)\"");

    private final Path socketPath;
    private ServerSocketChannel server;

    public UnixSocketIpc(Path socketPath) {
        this.socketPath = socketPath;
    }

    public boolean start() {
        try {
            Files.deleteIfExists(socketPath);
            Files.createDirectories(socketPath.getParent());
            var address = UnixDomainSocketAddress.of(socketPath);
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(address);
            var thread = new Thread(this::acceptLoop, "AkivCraft Unix socket IPC");
            thread.setDaemon(true);
            thread.start();
            System.out.printf("AkivCraft Unix socket IPC listening on %s%n", socketPath);
            return true;
        } catch (IOException error) {
            System.err.printf("AkivCraft Unix socket IPC failed: %s%n", error.getMessage());
            return false;
        }
    }

    private void acceptLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try (var channel = server.accept();
                 var reader = new BufferedReader(Channels.newReader(channel, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleLine(line);
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void handleLine(String line) {
        try {
            var type = extractType(line);
            var data = extractData(line);
            if ("bitmap".equals(type) && data != null) {
                var bitmaps = BinaryHudClient.parse(Base64.getDecoder().decode(data));
                BinaryHudClient.setBitmaps(bitmaps);
            } else if ("hud".equals(type) && data != null) {
                var text = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
                var items = NodeHudClient.parseLines(text.isBlank() ? List.of() : Arrays.asList(text.split("\\n", -1)));
                NodeHudClient.setItems(items);
            }
        } catch (Throwable error) {
            System.err.printf("AkivCraft Unix socket frame failed: %s%n", error.getMessage());
        }
    }

    private static String extractType(String json) {
        var matcher = TYPE_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractData(String json) {
        var matcher = DATA_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
