package me.morok.fControlPlayer.protocol.compat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

import java.util.Set;

public interface ProtocolLibCompat {
    Set<PacketType> buildForwardTypes();

    Set<PacketType> buildChatTypes();

    boolean isCommandType(PacketType type);

    String readChatMessage(PacketContainer packet);
}
