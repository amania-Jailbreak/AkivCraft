package dev.akivcraft.loader.via;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import io.netty.channel.ChannelHandlerContext;

import static com.viaversion.viaversion.platform.ViaChannelInitializer.reorderPipeline;

public final class AkivViaDecodeHandler extends ViaDecodeHandler {
    public AkivViaDecodeHandler(UserConnection connection) {
        super(connection);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        var event = String.valueOf(evt);
        if (evt instanceof ViaNetworkHook.PipelineReorderEvent
            || "COMPRESSION_THRESHOLD_UPDATED".equals(event)
            || "COMPRESSION_ENABLED".equals(event)) {
            reorderPipeline(ctx.pipeline(), "compress", "decompress");
        }
        super.userEventTriggered(ctx, evt);
    }
}
