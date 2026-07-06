package dev.akivcraft.loader.via;

import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.platform.providers.ViaProviders;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.commands.ViaCommandHandler;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class ViaBootstrap {
    private static volatile boolean initialized;

    private ViaBootstrap() {
    }

    public static void init(Path homeDir) {
        if (initialized) return;
        synchronized (ViaBootstrap.class) {
            if (initialized) return;
            initialized = true;
        }

        try {
            var dataFolder = new File(homeDir.toFile(), "via");
            ViaConfigStore.init(homeDir);
            var platform = new AkivViaPlatform(dataFolder);

            var loader = new ViaPlatformLoader() {
                @Override
                public void load() {}
                @Override
                public void unload() {}
            };

            var manager = ViaManagerImpl.builder()
                .platform(platform)
                .injector(new AkivViaInjector())
                .loader(loader)
                .commandHandler(new ViaCommandHandler())
                .build();

            Via.init(manager);
            platform.getConf().reload();
            manager.init();
            manager.getProviders().use(VersionProvider.class, new AkivVersionProvider());

            AkivViaProtocol.INSTANCE.initialize();

            ProtocolVersion.register(-2, "AUTO");

            loadBackwards(platform.getLogger());
            loadRewind(platform.getLogger());
            manager.onServerLoaded();

            System.out.println("AkivCraft ViaVersion initialized");
        } catch (Throwable error) {
            System.err.println("AkivCraft failed to initialize ViaVersion: " + error.getMessage());
            error.printStackTrace();
        }
    }

    private static void loadBackwards(Logger logger) {
        try {
            var clazz = Class.forName("com.viaversion.viabackwards.ViaBackwards");
            var instance = clazz.getDeclaredConstructor().newInstance();
            var initMethod = clazz.getMethod("init", java.io.File.class);
            initMethod.invoke(instance, new File(System.getProperty("user.home"), ".akivcraft/via"));
            logger.info("Loaded ViaBackwards");
        } catch (Throwable error) {
            logger.warning("ViaBackwards not loaded: " + error.getMessage());
        }
    }

    private static void loadRewind(Logger logger) {
        try {
            var clazz = Class.forName("com.viaversion.viarewind.ViaRewind");
            var instance = clazz.getDeclaredConstructor().newInstance();
            var initMethod = clazz.getMethod("init", java.io.File.class);
            initMethod.invoke(instance, new File(System.getProperty("user.home"), ".akivcraft/via"));
            logger.info("Loaded ViaRewind");
        } catch (Throwable error) {
            logger.warning("ViaRewind not loaded: " + error.getMessage());
        }
    }
}
