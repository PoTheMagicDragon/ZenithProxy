package com.zenith.network.client.handler.incoming.entity;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.network.client.ClientSession;
import com.zenith.network.registry.AsyncPacketHandler;
import lombok.NonNull;

import static com.zenith.Shared.*;

public class MoveEntityPosRotHandler implements AsyncPacketHandler<ClientboundMoveEntityPosRotPacket, ClientSession> {
    public static void trackPlayerVisualRangePosition(final Entity entity) {
        if (CONFIG.client.extra.visualRangePositionTracking && entity instanceof EntityPlayer && !((EntityPlayer) entity).isSelfPlayer()) {
            CACHE.getTabListCache().get(entity.getUuid()).ifPresent(playerEntry -> {
                if (!PLAYER_LISTS.getFriendsList().contains(playerEntry.getProfileId())) {
                    CLIENT_LOG.info("Tracking {}: {}, {}, {}", playerEntry.getName(), entity.getX(), entity.getY(), entity.getZ());
                }
            });
        }
    }

    @Override
    public boolean applyAsync(@NonNull ClientboundMoveEntityPosRotPacket packet, @NonNull ClientSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity != null) {
            entity.setYaw(packet.getYaw())
                    .setPitch(packet.getPitch())
                    .setX(entity.getX() + packet.getMoveX())
                    .setY(entity.getY() + packet.getMoveY())
                    .setZ(entity.getZ() + packet.getMoveZ());
            trackPlayerVisualRangePosition(entity);
            return true;
        } else {
//            CLIENT_LOG.warn("Received ServerEntityPositionRotationPacket for invalid entity (id={})", packet.getEntityId());
            return false;
        }
    }
}
