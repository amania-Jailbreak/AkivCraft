package dev.akivcraft.loader;

import java.lang.instrument.Instrumentation;

public final class AkivCraftAgent {
    private AkivCraftAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        bootstrap("premain", agentArgs, instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        bootstrap("agentmain", agentArgs, instrumentation);
    }

    private static void bootstrap(String mode, String agentArgs, Instrumentation instrumentation) {
        var config = LoaderConfig.fromSystemProperties();
        System.setProperty("minecraft.launcher.brand", "akivcraft");
        for (var transformer : AkivCraftTransformerSet.create()) {
            instrumentation.addTransformer(transformer, false);
        }

        AkivCraftBootCoordinator.startAsync();
        AkivCraftRuntimeServices.start(config);

        AkivCraftLoadingLog.stage("AkivCraft agent installed");

        System.out.printf(
            "AkivCraft loader installed via %s for Minecraft %s with Node runtime %s, IPC %s%n",
            mode,
            config.minecraftVersion(),
            config.nodeRuntimeEntry(),
            config.useStdioIpc() ? "stdio" : "socket"
        );
    }
}
