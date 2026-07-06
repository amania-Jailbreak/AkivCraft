package dev.akivcraft.loader.via;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocol.version.BaseVersionProvider;
import com.viaversion.viaversion.api.connection.UserConnection;

public final class AkivVersionProvider extends BaseVersionProvider {
    private static volatile int clientSideVersion = -2;

    public static int getClientSideVersion() {
        return clientSideVersion;
    }

    public static void setClientSideVersion(int version) {
        clientSideVersion = version;
    }

    @Override
    public ProtocolVersion getClientProtocol(UserConnection connection) {
        return ProtocolVersion.v26_1;
    }

    @Override
    public ProtocolVersion getClosestServerProtocol(UserConnection connection) throws Exception {
        if (clientSideVersion == -2) {
            var version = super.getClosestServerProtocol(connection);
            System.out.printf("AkivCraft Via AUTO resolved server protocol: %s (%d)%n", version.getName(), version.getVersion());
            return version;
        }
        var version = ProtocolVersion.getProtocol(clientSideVersion);
        System.out.printf("AkivCraft Via using selected server protocol: %s (%d)%n", version.getName(), version.getVersion());
        return version;
    }
}
