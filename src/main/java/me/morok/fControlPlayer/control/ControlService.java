package me.morok.fControlPlayer.control;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import me.morok.fControlPlayer.config.Messages;
import me.morok.fControlPlayer.control.inventory.InventoryMirror;
import me.morok.fControlPlayer.control.inventory.InventorySnapshot;
import me.morok.fControlPlayer.control.view.CameraService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ControlService {
    private static final double POSITION_SYNC_DISTANCE_SQ = 4.0;
    private static final boolean USE_CAMERA = false;
    private static final byte SNEAKING_FLAG = 0x02;
    private static final long TELEPORT_GRACE_MS = 1000L;

    private final JavaPlugin plugin;
    private final ProtocolManager protocolManager;
    private final CameraService cameraService;
    private final InventoryMirror inventoryMirror;
    private final boolean cameraEnabled;
    private final Messages messages;
    private final Set<UUID> commandBypass = new HashSet<>();
    private final Set<UUID> inventoryOpenBypass = new HashSet<>();
    private final Map<UUID, ControlSession> sessionsByController = new HashMap<>();
    private final Map<UUID, ControlSession> sessionsByTarget = new HashMap<>();
    private Boolean wrappedDataValueSupported;
    private BukkitTask tickTask;

    public ControlService(JavaPlugin plugin, ProtocolManager protocolManager, Messages messages) {
        this.plugin = plugin;
        this.protocolManager = protocolManager;
        this.cameraService = new CameraService(protocolManager);
        this.inventoryMirror = new InventoryMirror();
        this.cameraEnabled = USE_CAMERA && cameraService.isSupported();
        this.messages = messages;
    }

    public boolean isController(Player player) {
        return sessionsByController.containsKey(player.getUniqueId());
    }

    public boolean isControlled(Player player) {
        return sessionsByTarget.containsKey(player.getUniqueId());
    }

    public ControlSession getSessionByController(Player controller) {
        return sessionsByController.get(controller.getUniqueId());
    }

    public ControlSession getSessionByTarget(Player target) {
        return sessionsByTarget.get(target.getUniqueId());
    }

    public Player getTarget(Player controller) {
        ControlSession session = getSessionByController(controller);
        if (session == null) {
            return null;
        }
        return Bukkit.getPlayer(session.getTargetId());
    }

    public Player getController(Player target) {
        ControlSession session = getSessionByTarget(target);
        if (session == null) {
            return null;
        }
        return Bukkit.getPlayer(session.getControllerId());
    }

    public Player getControlledByEntityId(int entityId) {
        for (ControlSession session : sessionsByTarget.values()) {
            Player target = Bukkit.getPlayer(session.getTargetId());
            if (target != null && target.getEntityId() == entityId) {
                return target;
            }
        }
        return null;
    }

    public void syncControllerToTarget(Player target, Location destination) {
        if (destination == null) {
            return;
        }
        ControlSession session = getSessionByTarget(target);
        if (session == null) {
            return;
        }
        if (!session.consumeSuppressTeleportMark()) {
            session.markTargetTeleported(System.currentTimeMillis());
        }
        Player controller = Bukkit.getPlayer(session.getControllerId());
        if (controller == null) {
            stopByTarget(target);
            return;
        }
        if (!needsTeleport(controller.getLocation(), destination)) {
            return;
        }
        controller.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        markCameraDirty(controller);
    }

    public boolean start(Player controller, Player target) {
        if (controller.getUniqueId().equals(target.getUniqueId())) {
            messages.send(controller, "cannot-control-self");
            return false;
        }
        if (isControlled(controller)) {
            messages.send(controller, "controller-controlled");
            return false;
        }
        if (isControlled(target)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("target", target.getName());
            messages.send(controller, "target-already-controlled", placeholders);
            return false;
        }
        if (isController(target)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("target", target.getName());
            messages.send(controller, "target-controlling-else", placeholders);
            return false;
        }

        ControlSession session = new ControlSession(
            controller.getUniqueId(),
            target.getUniqueId(),
            PlayerStateSnapshot.capture(controller),
            InventorySnapshot.capture(controller)
        );
        sessionsByController.put(controller.getUniqueId(), session);
        sessionsByTarget.put(target.getUniqueId(), session);

        applyControllerMask(controller, target);
        if (!moveControllerToTarget(controller, target)) {
            restoreController(controller, session);
            sessionsByController.remove(controller.getUniqueId());
            sessionsByTarget.remove(target.getUniqueId());
            return false;
        }
        inventoryMirror.syncToController(target, controller);
        if (cameraEnabled) {
            session.setCameraApplied(cameraService.apply(controller, target));
        } else {
            session.setCameraApplied(false);
        }
        syncSneakState(controller, target);
        syncVitals(controller, target);
        syncPotionEffects(session, controller, target);
        syncControlledClient(target);

        Map<String, String> controllerPlaceholders = new HashMap<>();
        controllerPlaceholders.put("target", target.getName());
        messages.send(controller, "start-controller", controllerPlaceholders);
        Map<String, String> targetPlaceholders = new HashMap<>();
        targetPlaceholders.put("controller", controller.getName());
        messages.send(target, "start-target", targetPlaceholders);

        ensureTicking();
        return true;
    }

    public boolean stop(Player controller) {
        return stop(controller, true);
    }

    public boolean stop(Player controller, boolean notifyController) {
        ControlSession session = sessionsByController.remove(controller.getUniqueId());
        if (session == null) {
            return false;
        }
        sessionsByTarget.remove(session.getTargetId());
        Player target = Bukkit.getPlayer(session.getTargetId());

        restoreController(controller, session);

        if (notifyController) {
            messages.send(controller, "stop-controller");
        }
        if (target != null) {
            messages.send(target, "stop-target");
        }

        stopTickingIfIdle();
        return true;
    }

    public boolean stopByTarget(Player target) {
        UUID targetId = target.getUniqueId();
        ControlSession session = sessionsByTarget.remove(targetId);
        if (session == null) {
            session = findSessionByTargetId(targetId);
        }
        if (session == null) {
            return false;
        }
        sessionsByController.remove(session.getControllerId());
        sessionsByTarget.remove(session.getTargetId());

        Player controller = Bukkit.getPlayer(session.getControllerId());
        if (controller != null) {
            restoreController(controller, session);
            messages.send(controller, "stop-controller");
        }

        stopTickingIfIdle();
        return true;
    }

    public void markCameraDirty(Player controller) {
        if (!cameraEnabled) {
            return;
        }
        ControlSession session = getSessionByController(controller);
        if (session != null) {
            session.setCameraApplied(false);
        }
    }

    public void markControllerInventoryDirty(Player controller) {
        ControlSession session = getSessionByController(controller);
        if (session != null) {
            session.setControllerInventoryDirty(true);
        }
    }

    public void markTargetInventoryDirty(Player controller) {
        ControlSession session = getSessionByController(controller);
        if (session != null) {
            session.setTargetInventoryDirty(true);
        }
    }

    public void markInventoryOpenBypass(Player player) {
        if (player != null) {
            inventoryOpenBypass.add(player.getUniqueId());
        }
    }

    public boolean consumeInventoryOpenBypass(Player player) {
        if (player == null) {
            return false;
        }
        return inventoryOpenBypass.remove(player.getUniqueId());
    }

    public boolean isCommandBypassed(Player player) {
        return commandBypass.contains(player.getUniqueId());
    }

    public void runCommandAsTarget(Player target, String command) {
        UUID targetId = target.getUniqueId();
        commandBypass.add(targetId);
        try {
            target.performCommand(command);
        } finally {
            commandBypass.remove(targetId);
        }
    }

    public void forwardChatAsTarget(Player controller, String message) {
        ControlSession session = getSessionByController(controller);
        if (session == null) {
            return;
        }
        UUID targetId = session.getTargetId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null) {
                stop(controller, false);
                return;
            }
            Set<Player> recipients = new HashSet<>(Bukkit.getOnlinePlayers());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                AsyncPlayerChatEvent chatEvent = new AsyncPlayerChatEvent(true, target, message, recipients);
                Bukkit.getPluginManager().callEvent(chatEvent);
                if (chatEvent.isCancelled()) {
                    return;
                }
                String formatted = String.format(chatEvent.getFormat(),
                    chatEvent.getPlayer().getDisplayName(), chatEvent.getMessage());
                Set<Player> finalRecipients = new HashSet<>(chatEvent.getRecipients());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player recipient : finalRecipients) {
                        if (recipient.isOnline()) {
                            recipient.sendMessage(formatted);
                        }
                    }
                });
            });
        });
    }

    public boolean shouldIgnoreTeleportConfirm(Player target, int teleportId) {
        ControlSession session = getSessionByTarget(target);
        if (session == null) {
            return false;
        }
        return teleportId < 0;
    }

    public void hideControllersFrom(Player viewer) {
        for (ControlSession session : sessionsByController.values()) {
            Player controller = Bukkit.getPlayer(session.getControllerId());
            if (controller != null && !controller.getUniqueId().equals(viewer.getUniqueId())) {
                viewer.hidePlayer(plugin, controller);
            }
        }
    }

    public void shutdown() {
        List<ControlSession> sessions = new ArrayList<>(sessionsByController.values());
        for (ControlSession session : sessions) {
            Player controller = Bukkit.getPlayer(session.getControllerId());
            if (controller != null) {
                restoreController(controller, session);
            }
        }
        sessionsByController.clear();
        sessionsByTarget.clear();
        stopTicking();
    }

    private void ensureTicking() {
        if (tickTask != null) {
            return;
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void stopTickingIfIdle() {
        if (!sessionsByController.isEmpty()) {
            return;
        }
        stopTicking();
    }

    private void stopTicking() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private ControlSession findSessionByTargetId(UUID targetId) {
        for (ControlSession session : sessionsByController.values()) {
            if (session.getTargetId().equals(targetId)) {
                return session;
            }
        }
        return null;
    }

    private void tick() {
        if (sessionsByController.isEmpty()) {
            stopTickingIfIdle();
            return;
        }
        List<ControlSession> sessions = new ArrayList<>(sessionsByController.values());
        for (ControlSession session : sessions) {
            Player controller = Bukkit.getPlayer(session.getControllerId());
            Player target = Bukkit.getPlayer(session.getTargetId());
            if (controller == null || target == null) {
                if (controller != null) {
                    stop(controller, false);
                } else if (target != null) {
                    stopByTarget(target);
                }
                continue;
            }

            if (cameraEnabled && !session.isCameraApplied()) {
                if (cameraService.apply(controller, target)) {
                    session.setCameraApplied(true);
                }
            }
            syncPosition(session, controller, target);
            syncSneakState(controller, target);
            if (session.isControllerInventoryDirty()) {
                inventoryMirror.syncToTarget(controller, target);
                session.setControllerInventoryDirty(false);
            }
            if (session.isTargetInventoryDirty()) {
                target.updateInventory();
                session.setTargetInventoryDirty(false);
            }
            inventoryMirror.syncToController(target, controller, false);
            syncVitals(controller, target);
            syncPotionEffects(session, controller, target);
            syncHeldSlot(controller, target);
            syncControlledClient(target);
        }
    }

    private void applyControllerMask(Player controller, Player target) {
        controller.setInvulnerable(true);
        controller.setInvisible(true);
        controller.setCollidable(false);
        controller.setGravity(target.hasGravity());
        controller.setGameMode(target.getGameMode());
        controller.setAllowFlight(target.getAllowFlight());
        controller.setFlying(target.isFlying());
        controller.setWalkSpeed(target.getWalkSpeed());
        controller.setFlySpeed(target.getFlySpeed());
        hideController(controller);
        hideTargetFromController(controller, target);
    }

    private void restoreController(Player controller, ControlSession session) {
        if (cameraEnabled) {
            cameraService.reset(controller);
        }
        session.getControllerInventory().apply(controller);
        session.getControllerState().restore(controller);
        syncPotionEffects(session, controller, controller);
        showController(controller);
        syncVitals(controller, controller);
        Player target = Bukkit.getPlayer(session.getTargetId());
        if (target != null) {
            showTargetToController(controller, target);
        }
    }

    private void hideController(Player controller) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(controller.getUniqueId())) {
                viewer.hidePlayer(plugin, controller);
            }
        }
    }

    private void showController(Player controller) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(controller.getUniqueId())) {
                viewer.showPlayer(plugin, controller);
            }
        }
    }

    private void hideTargetFromController(Player controller, Player target) {
        controller.hidePlayer(plugin, target);
    }

    private void showTargetToController(Player controller, Player target) {
        controller.showPlayer(plugin, target);
    }

    private boolean moveControllerToTarget(Player controller, Player target) {
        Location destination = target.getLocation();
        if (!needsTeleport(controller.getLocation(), destination)) {
            return true;
        }
        boolean teleported = controller.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        markCameraDirty(controller);
        return teleported && !needsTeleport(controller.getLocation(), destination);
    }

    private void syncPosition(ControlSession session, Player controller, Player target) {
        if (session != null && session.isRecentTargetTeleport(System.currentTimeMillis(), TELEPORT_GRACE_MS)) {
            if (needsTeleport(controller.getLocation(), target.getLocation())) {
                controller.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                markCameraDirty(controller);
            }
            return;
        }
        Location controllerLoc = controller.getLocation();
        Location targetLoc = target.getLocation();
        if (!needsSync(targetLoc, controllerLoc)) {
            return;
        }
        if (session != null) {
            session.suppressNextTeleportMark();
        }
        target.teleport(controllerLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    private void syncVitals(Player controller, Player source) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.UPDATE_HEALTH);
        packet.getFloat().write(0, (float) source.getHealth());
        packet.getIntegers().write(0, source.getFoodLevel());
        packet.getFloat().write(1, source.getSaturation());
        try {
            protocolManager.sendServerPacket(controller, packet);
        } catch (Exception ignored) {
            // Best effort: keep control running even if the HUD update fails.
        }
        controller.setAbsorptionAmount(source.getAbsorptionAmount());
    }

    private void syncPotionEffects(ControlSession session, Player controller, Player target) {
        if (session == null) {
            return;
        }
        Map<PotionEffectType, EffectSnapshot> sent = session.getSyncedEffects();
        for (EffectSnapshot snapshot : sent.values()) {
            snapshot.tickDown();
        }
        Map<PotionEffectType, EffectSnapshot> current = new HashMap<>();
        for (PotionEffect effect : target.getActivePotionEffects()) {
            current.put(effect.getType(), EffectSnapshot.from(effect));
        }
        if (!sent.isEmpty()) {
            List<PotionEffectType> toRemove = new ArrayList<>();
            for (PotionEffectType type : sent.keySet()) {
                if (!current.containsKey(type)) {
                    toRemove.add(type);
                }
            }
            for (PotionEffectType type : toRemove) {
                sendRemoveEffect(controller, type);
                sent.remove(type);
            }
        }
        for (Map.Entry<PotionEffectType, EffectSnapshot> entry : current.entrySet()) {
            PotionEffectType type = entry.getKey();
            EffectSnapshot snapshot = entry.getValue();
            EffectSnapshot previous = sent.get(type);
            if (previous == null || previous.shouldResend(snapshot)) {
                sendAddEffect(controller, type, snapshot);
                sent.put(type, snapshot);
            }
        }
    }

    private void sendAddEffect(Player controller, PotionEffectType type, EffectSnapshot effect) {
        if (type == null || effect == null) {
            return;
        }
        PacketType packetType = PacketType.Play.Server.ENTITY_EFFECT;
        if (packetType == null || !packetType.isSupported()) {
            return;
        }
        PacketContainer packet = protocolManager.createPacket(packetType);
        if (packet.getIntegers().size() > 0) {
            packet.getIntegers().write(0, controller.getEntityId());
        }
        boolean usesEffectType = false;
        if (packet.getEffectTypes().size() > 0) {
            try {
                packet.getEffectTypes().write(0, type);
                usesEffectType = true;
            } catch (Exception ignored) {
                return;
            }
        }
        int amplifierIndex = usesEffectType ? 0 : 1;
        int flagsIndex = usesEffectType ? 1 : 2;
        if (!usesEffectType) {
            int effectId = type.getId();
            if (packet.getBytes().size() > 0) {
                packet.getBytes().write(0, (byte) effectId);
            }
        }
        if (packet.getBytes().size() > amplifierIndex) {
            packet.getBytes().write(amplifierIndex, (byte) effect.getAmplifier());
        }
        if (packet.getIntegers().size() > 1) {
            packet.getIntegers().write(1, effect.getDuration());
        }
        if (packet.getBytes().size() > flagsIndex) {
            byte flags = 0;
            if (effect.isAmbient()) {
                flags |= 0x01;
            }
            if (effect.hasParticles()) {
                flags |= 0x02;
            }
            if (effect.hasIcon()) {
                flags |= 0x04;
            }
            packet.getBytes().write(flagsIndex, flags);
        }
        try {
            protocolManager.sendServerPacket(controller, packet);
        } catch (Exception ignored) {
            // Best effort: keep control running even if effect sync fails.
        }
    }

    private void sendRemoveEffect(Player controller, PotionEffectType type) {
        if (type == null) {
            return;
        }
        PacketType packetType = PacketType.Play.Server.REMOVE_ENTITY_EFFECT;
        if (packetType == null || !packetType.isSupported()) {
            return;
        }
        PacketContainer packet = protocolManager.createPacket(packetType);
        if (packet.getIntegers().size() > 0) {
            packet.getIntegers().write(0, controller.getEntityId());
        }
        boolean usesEffectType = false;
        if (packet.getEffectTypes().size() > 0) {
            try {
                packet.getEffectTypes().write(0, type);
                usesEffectType = true;
            } catch (Exception ignored) {
                return;
            }
        }
        if (!usesEffectType) {
            int effectId = type.getId();
            if (packet.getBytes().size() > 0) {
                packet.getBytes().write(0, (byte) effectId);
            } else if (packet.getIntegers().size() > 1) {
                packet.getIntegers().write(1, effectId);
            }
        }
        try {
            protocolManager.sendServerPacket(controller, packet);
        } catch (Exception ignored) {
            // Best effort: keep control running even if effect removal fails.
        }
    }

    private void syncSneakState(Player controller, Player target) {
        boolean shouldSneak = controller.isSneaking();
        if (target.isSneaking() != shouldSneak) {
            target.setSneaking(shouldSneak);
        }
        PacketType type = PacketType.Play.Server.ENTITY_METADATA;
        if (type == null || !type.isSupported()) {
            return;
        }
        WrappedDataWatcher source = WrappedDataWatcher.getEntityWatcher(target);
        Byte flags = source.getByte(0);
        byte current = flags == null ? 0 : flags;
        byte updated = shouldSneak ? (byte) (current | SNEAKING_FLAG) : (byte) (current & ~SNEAKING_FLAG);

        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
        if (byteSerializer == null) {
            return;
        }

        PacketContainer packet = protocolManager.createPacket(type);
        if (packet.getIntegers().size() > 0) {
            packet.getIntegers().write(0, target.getEntityId());
        }
        boolean wroteDataValue = false;
        if (packet.getDataValueCollectionModifier().size() > 0 && isWrappedDataValueSupported()) {
            try {
                List<WrappedDataValue> dataValues = new ArrayList<>();
                dataValues.add(new WrappedDataValue(0, byteSerializer, updated));
                packet.getDataValueCollectionModifier().write(0, dataValues);
                wroteDataValue = true;
            } catch (Throwable ignored) {
                wrappedDataValueSupported = false;
            }
        }
        if (!wroteDataValue) {
            if (packet.getWatchableCollectionModifier().size() == 0) {
                return;
            }
            List<WrappedWatchableObject> watchables = new ArrayList<>();
            watchables.add(new WrappedWatchableObject(
                new WrappedDataWatcher.WrappedDataWatcherObject(0, byteSerializer), updated));
            packet.getWatchableCollectionModifier().write(0, watchables);
        }
        try {
            protocolManager.sendServerPacket(target, packet);
        } catch (Exception ignored) {
            // Best effort: keep control running even if sneak sync fails.
        }
    }

    private boolean isWrappedDataValueSupported() {
        if (wrappedDataValueSupported != null) {
            return wrappedDataValueSupported;
        }
        boolean supported = false;
        try {
            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);
            if (serializer != null) {
                new WrappedDataValue(0, serializer, (byte) 0);
                supported = true;
            }
        } catch (Throwable ignored) {
            supported = false;
        }
        wrappedDataValueSupported = supported;
        return supported;
    }

    private void syncHeldSlot(Player controller, Player target) {
        PacketType type = PacketType.Play.Server.HELD_ITEM_SLOT;
        if (type == null || !type.isSupported()) {
            return;
        }
        PacketContainer packet = protocolManager.createPacket(type);
        int slot = controller.getInventory().getHeldItemSlot();
        if (packet.getIntegers().size() > 0) {
            packet.getIntegers().write(0, slot);
        } else if (packet.getBytes().size() > 0) {
            packet.getBytes().write(0, (byte) slot);
        } else {
            return;
        }
        try {
            protocolManager.sendServerPacket(target, packet);
        } catch (Exception ignored) {
            // Best effort: keep control running even if held slot sync fails.
        }
    }

    private void syncControlledClient(Player target) {
        ControlSession session = getSessionByTarget(target);
        if (session == null) {
            return;
        }
        PacketType type = PacketType.Play.Server.POSITION;
        if (type == null || !type.isSupported()) {
            return;
        }
        Location loc = target.getLocation();
        PacketContainer packet = protocolManager.createPacket(type);
        packet.getModifier().writeDefaults();
        if (packet.getDoubles().size() >= 3) {
            packet.getDoubles().write(0, loc.getX());
            packet.getDoubles().write(1, loc.getY());
            packet.getDoubles().write(2, loc.getZ());
        }
        if (packet.getFloat().size() >= 2) {
            packet.getFloat().write(0, loc.getYaw());
            packet.getFloat().write(1, loc.getPitch());
        }
        if (packet.getIntegers().size() > 0) {
            packet.getIntegers().write(0, session.nextClientTeleportId());
        }
        if (packet.getBytes().size() > 0) {
            packet.getBytes().write(0, (byte) 0);
        }
        if (packet.getBooleans().size() > 0) {
            packet.getBooleans().write(0, false);
        }
        try {
            protocolManager.sendServerPacket(target, packet);
        } catch (Exception ignored) {
            // Best effort: keep control running even if view sync fails.
        }
    }

    private boolean needsSync(Location currentLoc, Location desiredLoc) {
        if (currentLoc.getWorld() != desiredLoc.getWorld()) {
            return true;
        }
        double dx = currentLoc.getX() - desiredLoc.getX();
        double dy = currentLoc.getY() - desiredLoc.getY();
        double dz = currentLoc.getZ() - desiredLoc.getZ();
        return dx * dx + dy * dy + dz * dz > POSITION_SYNC_DISTANCE_SQ;
    }

    private boolean needsTeleport(Location currentLoc, Location desiredLoc) {
        if (currentLoc.getWorld() != desiredLoc.getWorld()) {
            return true;
        }
        double dx = currentLoc.getX() - desiredLoc.getX();
        double dy = currentLoc.getY() - desiredLoc.getY();
        double dz = currentLoc.getZ() - desiredLoc.getZ();
        return dx * dx + dy * dy + dz * dz > 0.0001;
    }
}
