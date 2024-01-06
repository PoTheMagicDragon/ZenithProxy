package com.zenith.network.netty;

import com.github.steveice10.packetlib.packet.Packet;
import com.zenith.Proxy;
import com.zenith.network.client.ClientSession;
import com.zenith.network.server.ServerConnection;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.zenith.Shared.CLIENT_HANDLERS;
import static com.zenith.Shared.CLIENT_LOG;

@RequiredArgsConstructor
public class ZenithClientInboundChannelHandler extends MessageToMessageDecoder<Packet> {
    private final ClientSession session;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final Packet packet, final List<Object> out) {
        Packet p = CLIENT_HANDLERS.handleInbound(packet, this.session);
        if (p != null) {
            for (ServerConnection connection : Proxy.getInstance().getActiveConnections()) {
                connection.sendAsync(packet); // sends on each connection's own event loop
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        CLIENT_LOG.error("ZenithClientInboundChannelHandler Error", cause);
        super.exceptionCaught(ctx, cause);
    }
}
