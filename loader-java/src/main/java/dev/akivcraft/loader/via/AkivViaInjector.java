package dev.akivcraft.loader.via;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.platform.NoopInjector;

import java.util.TreeSet;

public final class AkivViaInjector extends NoopInjector {
    @Override
    public ProtocolVersion getServerProtocolVersion() {
        return ProtocolVersion.v26_1;
    }

    @Override
    public java.util.SortedSet<ProtocolVersion> getServerProtocolVersions() {
        var versions = new TreeSet<ProtocolVersion>();
        for (var v : ProtocolVersion.getProtocols()) {
            versions.add(v);
        }
        return versions;
    }
}
