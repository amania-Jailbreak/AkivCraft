package dev.akivcraft.loader;

public final class AkivCraftLauncherBootstrap {
    private AkivCraftLauncherBootstrap() {
    }

    public static void launch(String[] args) throws Exception {
        System.setProperty("minecraft.launcher.brand", "akivcraft");
        var config = LoaderConfig.fromSystemProperties();
        AkivCraftLoadingLog.stage("AkivCraft launcher bootstrap");
        AkivCraftRuntimeServices.start(config);
        AkivCraftBootCoordinator.startAsync();

        var main = Class.forName("net.minecraft.client.main.Main", true, Thread.currentThread().getContextClassLoader());
        var method = main.getMethod("main", String[].class);
        try {
            method.invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException error) {
            var cause = error.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error fatal) throw fatal;
            throw new RuntimeException(cause);
        }
    }
}
