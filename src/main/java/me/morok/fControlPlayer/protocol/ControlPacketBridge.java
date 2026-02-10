package me.morok.fControlPlayer.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.BlockPosition;
import me.morok.fControlPlayer.control.ControlService;
import me.morok.fControlPlayer.protocol.compat.ProtocolLibCompat;
import me.morok.fControlPlayer.protocol.compat.ProtocolLibPacketTypes;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ControlPacketBridge {
    private static final String META_KEY = "fcontrol-forwarded";

    private final ProtocolManager protocolManager;
    private final ControlService controlService;
    private final ProtocolLibCompat compat;
    private final PacketAdapter adapter;
    private final Set<PacketType> forwardTypes;
    private final Set<PacketType> blockedTypes;
    private final Set<PacketType> chatTypes;
    private final Set<PacketType> mirrorTypes;
    private final PacketType teleportConfirmType;
    private final PacketType heldItemSlotType;
    private final PacketType heldItemSlotServerType;
    private final PacketType armAnimationType;
    private final PacketType animationServerType;
    private final PacketType blockBreakAnimationType;
    private final Map<String, Long> breakAnimationSent = new ConcurrentHashMap<>();

    public ControlPacketBridge(Plugin plugin, ControlService controlService, ProtocolManager protocolManager,
                               ProtocolLibCompat compat) {
        this.protocolManager = protocolManager;
        this.controlService = controlService;
        this.compat = compat;
        this.forwardTypes = compat.buildForwardTypes();
        this.chatTypes = compat.buildChatTypes();
        this.blockedTypes = new HashSet<>(forwardTypes);
        this.mirrorTypes = buildMirrorTypes();
        this.teleportConfirmType = ProtocolLibPacketTypes.resolveClient("TELEPORT_CONFIRM");
        this.heldItemSlotType = ProtocolLibPacketTypes.resolveClient("HELD_ITEM_SLOT");
        this.heldItemSlotServerType = ProtocolLibPacketTypes.resolveServer("HELD_ITEM_SLOT");
        this.armAnimationType = ProtocolLibPacketTypes.resolveClient("ARM_ANIMATION");
        this.animationServerType = ProtocolLibPacketTypes.resolveServer("ANIMATION");
        this.blockBreakAnimationType = ProtocolLibPacketTypes.resolveServer("BLOCK_BREAK_ANIMATION");
        this.adapter = new PacketAdapter(plugin, ListenerPriority.NORMAL, collectListenerTypes()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleReceiving(event);
            }

            @Override
            public void onPacketSending(PacketEvent event) {
                handleSending(event);
            }
        };
    }

    public void register() {
        protocolManager.addPacketListener(adapter);
    }

    public void unregister() {
        protocolManager.removePacketListener(adapter);
    }

    private Collection<PacketType> collectListenerTypes() {
        Set<PacketType> types = new HashSet<>(blockedTypes);
        types.addAll(chatTypes);
        if (teleportConfirmType != null && teleportConfirmType.isSupported()) {
            types.add(teleportConfirmType);
        }
        if (blockBreakAnimationType != null && blockBreakAnimationType.isSupported()) {
            types.add(blockBreakAnimationType);
        }
        return new ArrayList<>(types);
    }

    private void handleReceiving(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getMeta(META_KEY).isPresent()) {
            return;
        }

        Player player = event.getPlayer();
        PacketType type = event.getPacketType();

        if (controlService.isController(player)) {
            if (chatTypes.contains(type)) {
                if (compat.isCommandType(type)) {
                    return;
                }
                String message = compat.readChatMessage(packet);
                if (message != null) {
                    event.setCancelled(true);
                    controlService.forwardChatAsTarget(player, message);
                    return;
                }
                Player target = controlService.getTarget(player);
                if (target == null) {
                    controlService.stop(player, false);
                    return;
                }
                event.setCancelled(true);
                forwardPacket(target, packet);
                return;
            }
            if (!forwardTypes.contains(type)) {
                return;
            }
            Player target = controlService.getTarget(player);
            if (target == null) {
                controlService.stop(player, false);
                return;
            }
            if (!mirrorTypes.contains(type)) {
                event.setCancelled(true);
            }
            forwardPacket(target, packet);
            if (heldItemSlotType != null && heldItemSlotServerType != null
                && type.equals(heldItemSlotType) && heldItemSlotServerType.isSupported()) {
                syncHeldSlot(target, packet);
            }
            if (armAnimationType != null && animationServerType != null
                && type.equals(armAnimationType) && animationServerType.isSupported()) {
                syncSwingAnimation(target, packet);
            }
            return;
        }

        if (controlService.isControlled(player)) {
            if (teleportConfirmType != null && type.equals(teleportConfirmType)) {
                if (packet.getIntegers().size() == 0) {
                    return;
                }
                int teleportId = packet.getIntegers().read(0);
                if (controlService.shouldIgnoreTeleportConfirm(player, teleportId)) {
                    event.setCancelled(true);
                }
                return;
            }
            if (blockedTypes.contains(type)) {
                event.setCancelled(true);
            }
        }
    }

    private void handleSending(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getMeta(META_KEY).isPresent()) {
            return;
        }
        if (blockBreakAnimationType == null || !event.getPacketType().equals(blockBreakAnimationType)) {
            return;
        }
        if (packet.getIntegers().size() == 0) {
            return;
        }
        int breakerId = packet.getIntegers().read(0);
        Player target = controlService.getControlledByEntityId(breakerId);
        if (target == null) {
            return;
        }
        Player receiver = event.getPlayer();
        if (receiver != null && receiver.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        BlockPosition position = null;
        if (packet.getBlockPositionModifier().size() > 0) {
            position = packet.getBlockPositionModifier().read(0);
        }
        if (position == null) {
            return;
        }
        Integer stageValue = readBreakStage(packet);
        if (stageValue == null) {
            return;
        }
        String key = breakerId + ":" + position.getX() + ":" + position.getY() + ":" + position.getZ()
            + ":" + stageValue;
        long now = System.currentTimeMillis();
        Long last = breakAnimationSent.get(key);
        if (last != null && now - last < 50L) {
            return;
        }
        breakAnimationSent.put(key, now);
        if (breakAnimationSent.size() > 2048) {
            breakAnimationSent.clear();
        }
        sendServerPacket(target, packet);
    }

    private Set<PacketType> buildMirrorTypes() {
        Set<PacketType> types = new HashSet<>();
        addIfSupported(types,
            "POSITION",
            "POSITION_LOOK",
            "LOOK",
            "FLYING",
            "GROUND",
            "VEHICLE_MOVE",
            "BOAT_MOVE",
            "STEER_VEHICLE",
            "ENTITY_ACTION",
            "HELD_ITEM_SLOT",
            "WINDOW_CLICK",
            "CLOSE_WINDOW",
            "SET_CREATIVE_SLOT",
            "PICK_ITEM",
            "PICK_ITEM_FROM_BLOCK"
        );
        return types;
    }

    private void addIfSupported(Set<PacketType> types, String... fieldNames) {
        for (String fieldName : fieldNames) {
            PacketType type = ProtocolLibPacketTypes.resolveClient(fieldName);
            if (type != null && type.isSupported()) {
                types.add(type);
            }
        }
    }

    private void syncHeldSlot(Player target, PacketContainer clientPacket) {
        int slot;
        if (clientPacket.getIntegers().size() > 0) {
            slot = clientPacket.getIntegers().read(0);
        } else if (clientPacket.getBytes().size() > 0) {
            slot = clientPacket.getBytes().read(0);
        } else {
            return;
        }
        PacketContainer serverPacket = protocolManager.createPacket(heldItemSlotServerType);
        if (serverPacket.getIntegers().size() > 0) {
            serverPacket.getIntegers().write(0, slot);
        } else if (serverPacket.getBytes().size() > 0) {
            serverPacket.getBytes().write(0, (byte) slot);
        } else {
            return;
        }
        try {
            protocolManager.sendServerPacket(target, serverPacket);
        } catch (Exception ignored) {
            // Best effort: keep control running even if held slot sync fails.
        }
    }

    private void forwardPacket(Player target, PacketContainer original) {
        PacketContainer cloned = original.deepClone();
        cloned.setMeta(META_KEY, Boolean.TRUE);
        try {
            protocolManager.receiveClientPacket(target, cloned, false);
        } catch (Exception ignored) {
            // Best effort forwarding to keep the session running.
        }
    }

    private void sendServerPacket(Player target, PacketContainer original) {
        PacketContainer cloned = original.deepClone();
        cloned.setMeta(META_KEY, Boolean.TRUE);
        try {
            protocolManager.sendServerPacket(target, cloned);
        } catch (Exception ignored) {
            // Best effort: keep control running even if animation sync fails.
        }
    }

    private Integer readBreakStage(PacketContainer packet) {
        if (packet.getBytes().size() > 0) {
            return (int) packet.getBytes().read(0);
        }
        if (packet.getIntegers().size() > 1) {
            return packet.getIntegers().read(1);
        }
        return null;
    }

    private void syncSwingAnimation(Player target, PacketContainer clientPacket) {
        PacketContainer serverPacket = protocolManager.createPacket(animationServerType);
        if (serverPacket.getIntegers().size() == 0) {
            return;
        }
        int animationId = resolveSwingAnimation(clientPacket);
        serverPacket.getIntegers().write(0, target.getEntityId());
        if (serverPacket.getIntegers().size() > 1) {
            serverPacket.getIntegers().write(1, animationId);
        }
        if (serverPacket.getBytes().size() > 0) {
            serverPacket.getBytes().write(0, (byte) animationId);
        }
        try {
            protocolManager.sendServerPacket(target, serverPacket);
        } catch (Exception ignored) {
            // Best effort: keep control running even if swing sync fails.
        }
    }

    private int resolveSwingAnimation(PacketContainer clientPacket) {
        EnumWrappers.Hand hand = null;
        if (clientPacket.getHands().size() > 0) {
            hand = clientPacket.getHands().read(0);
        }
        return hand == EnumWrappers.Hand.OFF_HAND ? 3 : 0;
    }

    // Intentionally no command bypass here; commands are handled in ControlListener.
}
