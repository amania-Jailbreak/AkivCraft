package dev.akivcraft.loader;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

public final class NodeRuntimeBridge {
    private final LoaderConfig config;
    private final NodePidLock pidLock;
    private Process process;

    public NodeRuntimeBridge(LoaderConfig config) {
        this.config = config;
        this.pidLock = new NodePidLock(config.akivcraftHome().resolve("node.pid"));
    }

    public boolean start() {
        if (!pidLock.tryLock()) return false;

        if (!Files.isRegularFile(config.nodeRuntimeEntry())) {
            System.err.printf(
                "AkivCraft Node runtime not found: %s%nCopy the full .akivcraft folder into the instance directory, or set -Dakivcraft.nodeRuntime=<path>.%n",
                config.nodeRuntimeEntry()
            );
            return false;
        }

        var command = new ArrayList<String>();
        command.add(config.nodeExecutable());
        command.add(config.nodeRuntimeEntry().toString());
        command.add("--minecraft-version");
        command.add(config.minecraftVersion());
        command.add("--mods");
        command.add(config.modsDirectory().toString());
        if (config.useStdioIpc()) {
            command.add("--stdio-ipc");
            command.add("--unix-socket");
            command.add(config.unixSocketPath().toString());
        } else {
            command.add("--port");
            command.add(Integer.toString(config.ipcPort()));
            command.add("--state-port");
            command.add(Integer.toString(config.statePort()));
            command.add("--binary-port");
            command.add(Integer.toString(config.binaryPort()));
            command.add("--udp-port");
            command.add(Integer.toString(config.udpPort()));
        }

        try {
            var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT);
            if (Files.isDirectory(config.akivcraftHome())) {
                builder.directory(config.akivcraftHome().toFile());
            }

            process = builder.start();
            if (config.useStdioIpc()) StdioIpcBridge.attach(process);
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "AkivCraft Node shutdown"));
            return true;
        } catch (IOException error) {
            System.err.printf(
                "Failed to start AkivCraft Node runtime with executable '%s': %s%nMinecraft will continue without AkivCraft Node mods.%nSet -Dakivcraft.node=/absolute/path/to/node if your launcher does not inherit shell PATH.%n",
                config.nodeExecutable(),
                error.getMessage()
            );
            return false;
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public void stop() {
        pidLock.release();
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
