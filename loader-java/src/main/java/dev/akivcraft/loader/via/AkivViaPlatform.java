package dev.akivcraft.loader.via;

import com.viaversion.viaversion.UserConnectionViaAPI;
import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.configuration.ViaVersionConfig;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.platform.ViaPlatform;
import com.viaversion.viaversion.configuration.AbstractViaConfig;

import java.io.File;
import java.util.logging.Logger;

public final class AkivViaPlatform implements ViaPlatform<UserConnection> {
    private final Logger logger = Logger.getLogger("AkivCraft-Via");
    private final File dataFolder;
    private final AbstractViaConfig config;
    private final ViaAPI<UserConnection> api;

    public AkivViaPlatform(File dataFolder) {
        this.dataFolder = dataFolder;
        this.dataFolder.mkdirs();
        this.config = new AbstractViaConfig(new File(dataFolder, "viaversion.yml"), logger) {
            @Override
            public java.util.List<String> getUnsupportedOptions() {
                return java.util.List.of();
            }
        };
        this.api = new UserConnectionViaAPI();
    }

    @Override
    public Logger getLogger() { return logger; }

    @Override
    public String getPlatformName() { return "AkivCraft"; }

    @Override
    public String getPlatformVersion() { return "0.1.0"; }

    @Override
    public boolean isProxy() { return true; }

    @Override
    public String getPluginVersion() { return "5.10.0"; }

    @Override
    public ViaAPI<UserConnection> getApi() { return api; }

    @Override
    public ViaVersionConfig getConf() { return config; }

    @Override
    public File getDataFolder() { return dataFolder; }
}
