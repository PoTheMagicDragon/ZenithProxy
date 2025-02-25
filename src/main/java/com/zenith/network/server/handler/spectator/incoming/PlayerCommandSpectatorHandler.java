package com.zenith.network.server.handler.spectator.incoming;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.Entity;
import com.zenith.feature.spectator.SpectatorUtils;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerConnection;

public class PlayerCommandSpectatorHandler implements PacketHandler<ServerboundPlayerCommandPacket, ServerConnection> {

    @Override
    public ServerboundPlayerCommandPacket apply(ServerboundPlayerCommandPacket packet, ServerConnection session) {
        Entity cameraTarget = session.getCameraTarget();
        if (cameraTarget != null) {
            if (packet.getState() == PlayerState.START_SNEAKING) {
                session.setCameraTarget(null);
                session.send(new ClientboundSetCameraPacket(session.getSpectatorSelfEntityId()));
                SpectatorUtils.syncSpectatorPositionToEntity(session, cameraTarget);
            }
        } else {
            if (packet.getState() == PlayerState.START_SNEAKING || packet.getState() == PlayerState.START_SPRINTING) {
                session.getSoundPacket().ifPresent(p -> {
                    Proxy.getInstance().getActiveConnections().forEach(connection -> connection.send(p));
                });
            }
        }
        return null;
    }
}
