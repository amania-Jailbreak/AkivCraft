package dev.akivcraft.loader;

public final class AkivCraftRuntimeServices {
    private static volatile boolean started;

    private AkivCraftRuntimeServices() {
    }

    public static void start(LoaderConfig config) {
        if (started) return;
        synchronized (AkivCraftRuntimeServices.class) {
            if (started) return;
            started = true;
        }

        var stateServerStarted = false;
        if (!config.useStdioIpc()) {
            var stateServer = new StateIpcServer(config.statePort());
            stateServerStarted = stateServer.start();
            NodeHudClient.start(config.ipcPort());
            BinaryHudClient.start(config.binaryPort());
            UdpHudClient.start(config.udpPort());
        }

        KeyEventBridge.start(config.ipcPort());
        ItemUseIpc.start(config.ipcPort());
        ChatCapture.start(config.ipcPort());
        AkivCraftKeyMappings.start(config.ipcPort());

        System.out.printf(
            "AkivCraft runtime services started: ipc=%d, state=%s, stdio=%s%n",
            config.ipcPort(),
            stateServerStarted ? "enabled" : "disabled",
            config.useStdioIpc() ? "enabled" : "disabled"
        );
    }
}
