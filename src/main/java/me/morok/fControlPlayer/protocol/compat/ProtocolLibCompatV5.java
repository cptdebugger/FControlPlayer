package me.morok.fControlPlayer.protocol.compat;

import com.comphenix.protocol.PacketType;

import java.util.Set;

public final class ProtocolLibCompatV5 extends BaseProtocolLibCompat {
    @Override
    public Set<PacketType> buildForwardTypes() {
        return buildCommonForwardTypes();
    }

    @Override
    public Set<PacketType> buildChatTypes() {
        return buildCommonChatTypes();
    }
}
