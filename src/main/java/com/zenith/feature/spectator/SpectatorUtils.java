package com.zenith.feature.spectator;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Equipment;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundRotateHeadPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddPlayerPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.zenith.Proxy;
import com.zenith.cache.CachedData;
import com.zenith.cache.DataCache;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.feature.spectator.entity.mob.SpectatorEntityEnderDragon;
import com.zenith.network.server.ServerConnection;
import com.zenith.util.math.MathHelper;

import java.util.Collection;
import java.util.function.Supplier;

import static com.github.steveice10.mc.protocol.data.game.entity.player.GameMode.SPECTATOR;
import static com.zenith.Shared.CACHE;
import static java.util.Arrays.asList;

public final class SpectatorUtils {

    public static void syncPlayerEquipmentWithSpectatorsFromCache() {
        sendSpectatorsEquipment();
    }

    public static void syncPlayerPositionWithSpectators() {
        Proxy.getInstance().getSpectatorConnections().forEach(connection -> {
            connection.sendAsync(new ClientboundTeleportEntityPacket(
                CACHE.getPlayerCache().getEntityId(),
                CACHE.getPlayerCache().getX(),
                CACHE.getPlayerCache().getY(),
                CACHE.getPlayerCache().getZ(),
                CACHE.getPlayerCache().getYaw(),
                CACHE.getPlayerCache().getPitch(),
                false // idk if this will break any rendering or not
            ));
            connection.sendAsync(new ClientboundRotateHeadPacket(
                CACHE.getPlayerCache().getEntityId(),
                CACHE.getPlayerCache().getYaw()
            ));
        });
    }

    public static void syncSpectatorPositionToEntity(final ServerConnection spectConnection, Entity target) {
        spectConnection.getSpectatorPlayerCache()
            .setX(target.getX())
            .setY(target.getY() + 1) // spawn above entity
            .setZ(target.getZ())
            .setYaw(target.getYaw())
            .setPitch(target.getPitch());
        spectConnection.setAllowSpectatorServerPlayerPosRotate(true);
        spectConnection.send(new ClientboundPlayerPositionPacket(
                spectConnection.getSpectatorPlayerCache().getX(),
                spectConnection.getSpectatorPlayerCache().getY(),
                spectConnection.getSpectatorPlayerCache().getZ(),
                spectConnection.getSpectatorPlayerCache().getYaw(),
                spectConnection.getSpectatorPlayerCache().getPitch(),
                12345
        ));
        spectConnection.setAllowSpectatorServerPlayerPosRotate(false);
        updateSpectatorPosition(spectConnection);
        Proxy.getInstance().getActiveConnections().forEach(c -> {
            if (!c.equals(spectConnection) || spectConnection.isShowSelfEntity()) {
                c.sendAsync(spectConnection.getEntitySpawnPacket());
                c.sendAsync(spectConnection.getEntityMetadataPacket());
            }
        });
    }

    public static void syncSpectatorPositionToProxiedPlayer(final ServerConnection spectConnection) {
        syncSpectatorPositionToEntity(spectConnection, CACHE.getPlayerCache().getThePlayer());
    }

    private static void sendSpectatorsEquipment() {
        Proxy.getInstance().getSpectatorConnections().forEach(SpectatorUtils::sendSpectatorsEquipment);
    }

    private static void sendSpectatorsEquipment(final ServerConnection connection) {
        connection.sendAsync(new ClientboundSetEquipmentPacket(
                CACHE.getPlayerCache().getEntityId(),
                CACHE.getPlayerCache().getThePlayer().getEquipment().entrySet().stream()
                    .map(entry -> new Equipment(entry.getKey(), entry.getValue()))
                    .toArray(Equipment[]::new)));
    }

    private static void spawnSpectatorForOtherSessions(ServerConnection session, ServerConnection connection) {
        if (connection.equals(Proxy.getInstance().getCurrentPlayer().get())) {
            session.sendAsync(new ClientboundAddPlayerPacket(
                    CACHE.getPlayerCache().getEntityId(),
                    CACHE.getProfileCache().getProfile().getId(),
                    CACHE.getPlayerCache().getX(),
                    CACHE.getPlayerCache().getY(),
                    CACHE.getPlayerCache().getZ(),
                    CACHE.getPlayerCache().getYaw(),
                    CACHE.getPlayerCache().getPitch()));
            session.sendAsync(new ClientboundSetEntityDataPacket(
                    CACHE.getPlayerCache().getEntityId(),
                    CACHE.getPlayerCache().getThePlayer().getEntityMetadataAsArray()));
        } else {
            session.sendAsync(connection.getEntitySpawnPacket());
            session.sendAsync(connection.getEntityMetadataPacket());
        }
        connection.sendAsync(session.getEntitySpawnPacket());
        connection.sendAsync(session.getEntityMetadataPacket());
    }

    public static EntityPlayer getSpectatorPlayerEntity(final ServerConnection session) {
        EntityPlayer spectatorEntityPlayer = new EntityPlayer();
        spectatorEntityPlayer.setUuid(session.getSpectatorFakeProfileCache().getProfile().getId());
        spectatorEntityPlayer.setSelfPlayer(true);
        spectatorEntityPlayer.setX(CACHE.getPlayerCache().getX());
        spectatorEntityPlayer.setY(CACHE.getPlayerCache().getY() + 1); // spawn above player
        spectatorEntityPlayer.setZ(CACHE.getPlayerCache().getZ());
        spectatorEntityPlayer.setEntityId(session.getSpectatorSelfEntityId());
        spectatorEntityPlayer.setYaw(CACHE.getPlayerCache().getYaw());
        spectatorEntityPlayer.setPitch(CACHE.getPlayerCache().getPitch());
        final CompoundTag emptyNbtTag = new CompoundTag("");
        emptyNbtTag.clear();
        spectatorEntityPlayer.setMetadata(asList(
            new FloatEntityMetadata(9, MetadataType.FLOAT, 20.0f), // health
            new ByteEntityMetadata(17, MetadataType.BYTE, (byte) 255) // visible skin parts
        ));
        return spectatorEntityPlayer;
    }

    public static void initSpectator(ServerConnection session, Supplier<Collection<CachedData>> cacheSupplier) {
        // update spectator player cache
        EntityPlayer spectatorEntityPlayer = getSpectatorPlayerEntity(session);
        session.getSpectatorPlayerCache()
            .setThePlayer(spectatorEntityPlayer)
            .setGameMode(SPECTATOR)
            .setEnableRespawnScreen(CACHE.getPlayerCache().isEnableRespawnScreen())
            .setLastDeathPos(CACHE.getPlayerCache().getLastDeathPos())
            .setPortalCooldown(CACHE.getPlayerCache().getPortalCooldown())
            .setHardcore(CACHE.getPlayerCache().isHardcore())
            .setReducedDebugInfo(CACHE.getPlayerCache().isReducedDebugInfo())
            .setHeldItemSlot(CACHE.getPlayerCache().getHeldItemSlot())
            .setEnabledFeatures(CACHE.getPlayerCache().getEnabledFeatures())
            .setDifficultyLocked(CACHE.getPlayerCache().isDifficultyLocked())
            .setInvincible(true)
            .setCanFly(true)
            .setCreative(false)
            .setFlying(true)
            .setFlySpeed(0.05f)
            .setWalkSpeed(0.1f)
            .setTags(CACHE.getPlayerCache().getTags())
            .setOpLevel(CACHE.getPlayerCache().getOpLevel())
            .setMaxPlayers(CACHE.getPlayerCache().getMaxPlayers());
        session.setAllowSpectatorServerPlayerPosRotate(true);
        DataCache.sendCacheData(cacheSupplier.get(), session);
        session.setAllowSpectatorServerPlayerPosRotate(false);
        session.sendAsync(session.getEntitySpawnPacket());
        session.sendAsync(session.getSelfEntityMetadataPacket());
        Proxy.getInstance().getActiveConnections().stream()
                .filter(connection -> !connection.equals(session))
                .forEach(connection -> {
                    spawnSpectatorForOtherSessions(session, connection);
                    connection.syncTeamMembers();
                });
        session.sendAsync(new ClientboundSetEntityDataPacket(session.getSpectatorSelfEntityId(), spectatorEntityPlayer.getEntityMetadataAsArray()));
        SpectatorUtils.syncPlayerEquipmentWithSpectatorsFromCache();
    }

    public static void checkSpectatorPositionOutOfRender(final ServerConnection spectConnection) {
        final int spectX = (int) spectConnection.getSpectatorPlayerCache().getX() >> 4;
        final int spectZ = (int) spectConnection.getSpectatorPlayerCache().getZ() >> 4;
        final int playerX = (int) CACHE.getPlayerCache().getX() >> 4;
        final int playerZ = (int) CACHE.getPlayerCache().getZ() >> 4;
        if (Math.abs(spectX - playerX) > (CACHE.getChunkCache().getRenderDistance() / 2 + 1) || Math.abs(spectZ - playerZ) > (CACHE.getChunkCache().getRenderDistance() / 2 + 1)) {
            SpectatorUtils.syncSpectatorPositionToProxiedPlayer(spectConnection);
        }
    }

    public static void updateSpectatorPosition(final ServerConnection selfSession) {
        if (selfSession.hasCameraTarget()) {
            return;
        }
        double playerEyeHeight = 1.6;
        double specEntityEyeHeight = selfSession.getSpectatorEntity().getEyeHeight();
        double specEntityTotalWidth = selfSession.getSpectatorEntity().getWidth();
        // clamping avoids moving the spectator entity directly in view of the player in 1st person at steep pitches
        float clampedPitch = MathHelper.clamp(selfSession.getSpectatorPlayerCache().getPitch(), -30, 30);

        double distance = (specEntityTotalWidth * -0.5) - 0.5;
        double yawRadians = Math.toRadians(selfSession.getSpectatorPlayerCache().getYaw());
        double pitchRadians = Math.toRadians(clampedPitch);

        double xOffset = -Math.sin(yawRadians) * Math.cos(pitchRadians) * distance;
        double yOffset = -Math.sin(pitchRadians) * distance;
        double zOffset = Math.cos(yawRadians) * Math.cos(pitchRadians) * distance;

        double newX = selfSession.getSpectatorPlayerCache().getX() + xOffset;
        double newY = selfSession.getSpectatorPlayerCache().getY() + playerEyeHeight - specEntityEyeHeight + yOffset;
        double newZ = selfSession.getSpectatorPlayerCache().getZ() + zOffset;
        Proxy.getInstance().getActiveConnections()
                .forEach(connection -> {
                    connection.sendAsync(new ClientboundTeleportEntityPacket(
                        selfSession.getSpectatorEntityId(),
                        newX,
                        newY,
                        newZ,
                        getDisplayYaw(selfSession),
                        selfSession.getSpectatorPlayerCache().getPitch(),
                        false
                    ));
                    connection.sendAsync(new ClientboundRotateHeadPacket(
                        selfSession.getSpectatorEntityId(),
                        getDisplayYaw(selfSession)
                    ));
                });
    }

    public static float getDisplayYaw(final ServerConnection serverConnection) {
        // idk why but dragon is displayed 180 degrees off from what you'd expect
        if (serverConnection.getSpectatorEntity() instanceof SpectatorEntityEnderDragon) {
            return serverConnection.getSpectatorPlayerCache().getYaw() - 180f;
        } else {
            return serverConnection.getSpectatorPlayerCache().getYaw();
        }
    }
}
