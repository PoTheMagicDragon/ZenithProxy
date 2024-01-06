package com.zenith.network.netty;

import com.github.steveice10.packetlib.packet.Packet;
import com.viaversion.viaversion.exception.CancelEncoderException;
import com.zenith.network.client.ClientSession;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.zenith.Shared.CLIENT_HANDLERS;

@RequiredArgsConstructor
public class ZenithClientOutboundChannelHandler extends MessageToMessageEncoder<Packet> {
    private final ClientSession session;

    @Override
    protected void encode(final ChannelHandlerContext channelHandlerContext, final Packet packet, final List<Object> out) {
        Packet p = CLIENT_HANDLERS.handleOutgoing(packet, this.session);
        if (p != null) out.add(p);
        else throw CancelEncoderException.generate(null);
    }
}
