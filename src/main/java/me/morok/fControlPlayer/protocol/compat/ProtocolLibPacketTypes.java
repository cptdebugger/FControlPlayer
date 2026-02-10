package me.morok.fControlPlayer.protocol.compat;

import com.comphenix.protocol.PacketType;

import java.lang.reflect.Field;

public final class ProtocolLibPacketTypes {
    private ProtocolLibPacketTypes() {
    }

    public static PacketType resolve(Class<?> holder, String fieldName) {
        if (holder == null || fieldName == null) {
            return null;
        }
        try {
            Field field = holder.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof PacketType) {
                return (PacketType) value;
            }
        } catch (NoSuchFieldException ignored) {
            // Field not available in this ProtocolLib version.
        } catch (IllegalAccessException ignored) {
            // Ignore and fall back gracefully.
        }
        return null;
    }

    public static PacketType resolveClient(String fieldName) {
        return resolve(PacketType.Play.Client.class, fieldName);
    }

    public static PacketType resolveServer(String fieldName) {
        return resolve(PacketType.Play.Server.class, fieldName);
    }
}
