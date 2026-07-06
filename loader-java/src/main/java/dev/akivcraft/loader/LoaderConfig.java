package dev.akivcraft.loader;

import java.net.URISyntaxException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.io.IOException;
import java.nio.file.Path;

public record LoaderConfig(
    String minecraftVersion,
    Path akivcraftHome,
    String nodeExecutable,
    Path nodeRuntimeEntry,
    Path modsDirectory,
    String ipcTransport,
    int ipcPort,
    int statePort,
    int binaryPort,
    int udpPort,
    Path unixSocketPath
) {
    public static LoaderConfig fromSystemProperties() {
        var version = System.getProperty("akivcraft.minecraftVersion", "26.1.2");
        var home = pathProperty("akivcraft.home", defaultHome());
        var nodeExecutable = System.getProperty("akivcraft.node", defaultNodeExecutable());
        var nodeRuntime = pathProperty("akivcraft.nodeRuntime", home.resolve("node-runtime/dist/index.js"));
        var modsDirectory = pathProperty("akivcraft.mods", home.resolve("mods"));
        var ipcTransport = System.getProperty("akivcraft.ipcTransport", "stdio");
        var useStdio = "stdio".equalsIgnoreCase(ipcTransport);
        var ipcPort = useStdio ? Integer.getInteger("akivcraft.ipcPort", 28512) : portProperty("akivcraft.ipcPort", 28512);
        var statePort = useStdio ? Integer.getInteger("akivcraft.statePort", 28513) : portProperty("akivcraft.statePort", 28513);
        var binaryPort = useStdio ? Integer.getInteger("akivcraft.binaryPort", 28514) : portProperty("akivcraft.binaryPort", 28514);
        var udpPort = useStdio ? Integer.getInteger("akivcraft.udpPort", 28515) : portProperty("akivcraft.udpPort", 28515);
        var unixSocketPath = pathProperty("akivcraft.unixSocket", home.resolve("ipc.sock"));

        return new LoaderConfig(version, home, nodeExecutable, nodeRuntime, modsDirectory, ipcTransport, ipcPort, statePort, binaryPort, udpPort, unixSocketPath);
    }

    public boolean useStdioIpc() {
        return "stdio".equalsIgnoreCase(ipcTransport);
    }

    private static Path defaultHome() {
        try {
            var codeSource = LoaderConfig.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                var location = codeSource.getLocation();
                if (location != null) {
                    var loaderPath = Path.of(location.toURI())
                        .toAbsolutePath()
                        .normalize();
                    var loaderDirectory = loaderPath.getParent();

                    if (loaderDirectory != null && ".akivcraft".equals(loaderDirectory.getFileName().toString())) {
                        return loaderDirectory;
                    }
                }
            }
        } catch (URISyntaxException | IllegalArgumentException | SecurityException | NullPointerException ignored) {
            // Fall back to the launcher working directory when the loader code source is unavailable.
        }

        return Path.of(System.getProperty("user.dir"), ".akivcraft");
    }

    private static Path pathProperty(String name, Path fallback) {
        return Path.of(System.getProperty(name, fallback.toString())).toAbsolutePath().normalize();
    }

    private static String defaultNodeExecutable() {
        var candidates = new String[] {
            "node",
            "/opt/homebrew/bin/node",
            "/usr/local/bin/node",
            "/usr/bin/node",
            System.getProperty("user.home", "") + "/.nvm/current/bin/node",
            System.getProperty("user.home", "") + "/.volta/bin/node",
            System.getProperty("user.home", "") + "/.fnm/current/bin/node",
            "C:\\Program Files\\nodejs\\node.exe",
            "C:\\Program Files (x86)\\nodejs\\node.exe"
        };

        for (var candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                if (!"node".equals(candidate) && java.nio.file.Files.isExecutable(Path.of(candidate))) return candidate;
            } catch (RuntimeException ignored) {
            }
        }

        return "node";
    }

    private static int portProperty(String name, int preferred) {
        if (System.getProperty(name) != null) return Integer.getInteger(name, preferred);

        if (isPortAvailable(preferred)) return preferred;
        try (var socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress())) {
            var port = socket.getLocalPort();
            System.out.printf("AkivCraft %s port %d is busy; using %d%n", name, preferred, port);
            return port;
        } catch (IOException error) {
            System.err.printf("AkivCraft failed to allocate dynamic port for %s: %s%n", name, error.getMessage());
            return preferred;
        }
    }

    private static boolean isPortAvailable(int port) {
        try (var ignored = new ServerSocket(port, 8, InetAddress.getLoopbackAddress())) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
