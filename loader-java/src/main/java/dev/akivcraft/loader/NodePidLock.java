package dev.akivcraft.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class NodePidLock {
    private final Path pidFile;

    public NodePidLock(Path pidFile) {
        this.pidFile = pidFile;
    }

    public boolean tryLock() {
        try {
            if (Files.isRegularFile(pidFile)) {
                var pid = readPid();
                if (pid > 0 && isProcessAlive(pid)) {
                    System.err.printf("AkivCraft Node runtime is already running (pid %d). Not starting another instance.%n", pid);
                    return false;
                }
                System.out.printf("AkivCraft stale Node pid file found (%d); replacing%n", pid);
            }
            Files.createDirectories(pidFile.getParent());
            Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException error) {
            System.err.printf("AkivCraft failed to write pid file %s: %s%n", pidFile, error.getMessage());
            return false;
        }
    }

    public void release() {
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException error) {
            System.err.printf("AkivCraft failed to remove pid file %s: %s%n", pidFile, error.getMessage());
        }
    }

    private long readPid() {
        try {
            var text = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            return Long.parseLong(text);
        } catch (IOException | NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
