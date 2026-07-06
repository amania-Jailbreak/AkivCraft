package dev.akivcraft.loader;

public final class AkivCraftMain {
    private AkivCraftMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("minecraft.launcher.brand", "akivcraft");
        System.setProperty("akivcraft.launcherMode", "true");

        var parent = AkivCraftMain.class.getClassLoader();
        var loader = new AkivCraftTransformingClassLoader(parent, AkivCraftTransformerSet.create(), AkivCraftTransformerSet.transformTargets());
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            var bootstrap = Class.forName("dev.akivcraft.loader.AkivCraftLauncherBootstrap", true, loader);
            var launch = bootstrap.getMethod("launch", String[].class);
            launch.invoke(null, (Object) args);
        } catch (java.lang.reflect.InvocationTargetException error) {
            var cause = error.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error fatal) throw fatal;
            throw new RuntimeException(cause);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
