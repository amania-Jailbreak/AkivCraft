package dev.akivcraft.loader.via;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.PacketFlow;

public final class ViaNetworkHook {
    private ViaNetworkHook() {
    }

    public static void onConfigureSerialization(ChannelPipeline pipeline, PacketFlow flow) {
        try {
            Channel channel = pipeline.channel();
            if (!(channel instanceof SocketChannel)) return;

            boolean clientSide = (flow == PacketFlow.CLIENTBOUND);
            UserConnection user = new UserConnectionImpl(channel, clientSide);
            new ProtocolPipelineImpl(user).add(AkivViaProtocol.INSTANCE);

            String encoderBefore = clientSide ? HandlerNames.ENCODER : HandlerNames.OUTBOUND_CONFIG;
            String decoderBefore = clientSide ? HandlerNames.INBOUND_CONFIG : HandlerNames.DECODER;

            pipeline.addBefore(encoderBefore, ViaEncodeHandler.NAME, new ViaEncodeHandler(user));
            pipeline.addBefore(decoderBefore, AkivViaDecodeHandler.NAME, new AkivViaDecodeHandler(user));
            System.out.printf("AkivCraft Via injected pipeline side=%s encoderBefore=%s decoderBefore=%s%n", flow, encoderBefore, decoderBefore);
        } catch (Throwable error) {
            System.err.println("AkivCraft Via network hook failed: " + error.getMessage());
        }
    }

    public static void onSetupCompression(ChannelPipeline pipeline) {
        try {
            pipeline.fireUserEventTriggered(new PipelineReorderEvent());
        } catch (Throwable ignored) {
        }
    }

    public static final class PipelineReorderEvent {
    }
}
