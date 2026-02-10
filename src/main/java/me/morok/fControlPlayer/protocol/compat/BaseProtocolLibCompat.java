package me.morok.fControlPlayer.protocol.compat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

abstract class BaseProtocolLibCompat implements ProtocolLibCompat {
    private final Set<PacketType> commandTypes;

    protected BaseProtocolLibCompat() {
        this.commandTypes = Collections.unmodifiableSet(buildCommandTypes());
    }

    @Override
    public boolean isCommandType(PacketType type) {
        return commandTypes.contains(type);
    }

    @Override
    public String readChatMessage(PacketContainer packet) {
        try {
            if (packet.getStrings().size() > 0) {
                return packet.getStrings().read(0);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    protected void addIfSupported(Set<PacketType> types, Class<?> holder, String... fieldNames) {
        for (String fieldName : fieldNames) {
            PacketType type = ProtocolLibPacketTypes.resolve(holder, fieldName);
            if (type != null && type.isSupported()) {
                types.add(type);
            }
        }
    }

    protected Set<PacketType> buildCommonForwardTypes() {
        Set<PacketType> types = new HashSet<>();
        addIfSupported(types, PacketType.Play.Client.class,
            "POSITION",
            "POSITION_LOOK",
            "LOOK",
            "FLYING",
            "GROUND",
            "VEHICLE_MOVE",
            "BOAT_MOVE",
            "STEER_VEHICLE",
            "ENTITY_ACTION",
            "BLOCK_DIG",
            "BLOCK_PLACE",
            "USE_ITEM",
            "USE_ITEM_ON",
            "ARM_ANIMATION",
            "USE_ENTITY",
            "HELD_ITEM_SLOT",
            "WINDOW_CLICK",
            "CLOSE_WINDOW",
            "SET_CREATIVE_SLOT",
            "CLIENT_COMMAND",
            "PICK_ITEM",
            "PICK_ITEM_FROM_BLOCK"
        );
        return types;
    }

    protected Set<PacketType> buildCommonChatTypes() {
        Set<PacketType> types = new HashSet<>();
        addIfSupported(types, PacketType.Play.Client.class,
            "CHAT",
            "CHAT_COMMAND",
            "CHAT_COMMAND_SIGNED"
        );
        return types;
    }

    private Set<PacketType> buildCommandTypes() {
        Set<PacketType> types = new HashSet<>();
        addIfSupported(types, PacketType.Play.Client.class,
            "CHAT_COMMAND",
            "CHAT_COMMAND_SIGNED"
        );
        return types;
    }
}
