package me.morok.fControlPlayer.control;

import me.morok.fControlPlayer.control.inventory.InventorySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.potion.PotionEffectType;

public final class ControlSession {
    private final UUID controllerId;
    private final UUID targetId;
    private final PlayerStateSnapshot controllerState;
    private final InventorySnapshot controllerInventory;
    private boolean cameraApplied;
    private boolean controllerInventoryDirty;
    private boolean targetInventoryDirty;
    private int clientTeleportId;
    private long lastTargetTeleportMs;
    private boolean suppressTeleportMark;
    private final Map<PotionEffectType, EffectSnapshot> syncedEffects = new HashMap<>();

    public ControlSession(UUID controllerId, UUID targetId, PlayerStateSnapshot controllerState,
                          InventorySnapshot controllerInventory) {
        this.controllerId = controllerId;
        this.targetId = targetId;
        this.controllerState = controllerState;
        this.controllerInventory = controllerInventory;
    }

    public UUID getControllerId() {
        return controllerId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public PlayerStateSnapshot getControllerState() {
        return controllerState;
    }

    public InventorySnapshot getControllerInventory() {
        return controllerInventory;
    }

    public boolean isCameraApplied() {
        return cameraApplied;
    }

    public void setCameraApplied(boolean cameraApplied) {
        this.cameraApplied = cameraApplied;
    }

    public boolean isControllerInventoryDirty() {
        return controllerInventoryDirty;
    }

    public void setControllerInventoryDirty(boolean controllerInventoryDirty) {
        this.controllerInventoryDirty = controllerInventoryDirty;
    }

    public boolean isTargetInventoryDirty() {
        return targetInventoryDirty;
    }

    public void setTargetInventoryDirty(boolean targetInventoryDirty) {
        this.targetInventoryDirty = targetInventoryDirty;
    }

    public int nextClientTeleportId() {
        if (clientTeleportId == 0 || clientTeleportId == Integer.MIN_VALUE) {
            clientTeleportId = -1;
            return clientTeleportId;
        }
        clientTeleportId--;
        return clientTeleportId;
    }

    public void markTargetTeleported(long nowMs) {
        lastTargetTeleportMs = nowMs;
    }

    public boolean isRecentTargetTeleport(long nowMs, long windowMs) {
        return nowMs - lastTargetTeleportMs <= windowMs;
    }

    public void suppressNextTeleportMark() {
        suppressTeleportMark = true;
    }

    public boolean consumeSuppressTeleportMark() {
        boolean value = suppressTeleportMark;
        suppressTeleportMark = false;
        return value;
    }

    public int getClientTeleportId() {
        return clientTeleportId;
    }

    public Map<PotionEffectType, EffectSnapshot> getSyncedEffects() {
        return syncedEffects;
    }
}
