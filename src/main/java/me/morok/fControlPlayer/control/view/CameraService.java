package me.morok.fControlPlayer.control.view;

import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import me.morok.fControlPlayer.protocol.compat.ProtocolLibPacketTypes;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class CameraService {
    private final ProtocolManager protocolManager;
    private final com.comphenix.protocol.PacketType cameraPacketType;

    public CameraService(ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
        this.cameraPacketType = ProtocolLibPacketTypes.resolveServer("CAMERA");
    }

    public boolean isSupported() {
        return cameraPacketType != null && cameraPacketType.isSupported();
    }

    public boolean apply(Player viewer, Entity target) {
        if (!isSupported()) {
            return false;
        }
        PacketContainer packet = protocolManager.createPacket(cameraPacketType);
        packet.getIntegers().write(0, target.getEntityId());
        try {
            protocolManager.sendServerPacket(viewer, packet);
            return true;
        } catch (Exception ignored) {
            // Best effort: keep control running even if the camera packet fails.
        }
        return false;
    }

    public void reset(Player viewer) {
        apply(viewer, viewer);
    }
}
