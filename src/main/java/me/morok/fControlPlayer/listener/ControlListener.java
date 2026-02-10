package me.morok.fControlPlayer.listener;

import me.morok.fControlPlayer.control.ControlService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

public final class ControlListener implements Listener {
    private final ControlService controlService;

    public ControlListener(ControlService controlService) {
        this.controlService = controlService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (controlService.isControlled(player)) {
            if (!controlService.isCommandBypassed(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (!controlService.isController(player)) {
            return;
        }
        String message = event.getMessage();
        if (shouldBypassControllerCommand(message)) {
            return;
        }
        Player target = controlService.getTarget(player);
        if (target == null) {
            controlService.stop(player, false);
            event.setCancelled(true);
            return;
        }
        String command = message;
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        controlService.runCommandAsTarget(target, command);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        controlService.hideControllersFrom(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        cancelIfController(event.getPlayer(), event);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfController(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (controlService.isController(player)) {
                controlService.markControllerInventoryDirty(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (controlService.isController(player)) {
            event.setCancelled(true);
            controlService.markTargetInventoryDirty(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (controlService.isController(player)) {
            controlService.markControllerInventoryDirty(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (controlService.isController(player)) {
            controlService.markControllerInventoryDirty(player);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (controlService.isController(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (controlService.isController(player)) {
                if (controlService.consumeInventoryOpenBypass(player)) {
                    return;
                }
                InventoryType type = event.getInventory().getType();
                if (type != InventoryType.PLAYER && type != InventoryType.CRAFTING) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpenSync(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player target = (Player) event.getPlayer();
            if (controlService.isControlled(target)) {
                InventoryType type = event.getInventory().getType();
                if (type == InventoryType.PLAYER || type == InventoryType.CRAFTING) {
                    return;
                }
                Player controller = controlService.getController(target);
                if (controller == null) {
                    controlService.stopByTarget(target);
                    return;
                }
                if (controller.getOpenInventory() != null
                    && controller.getOpenInventory().getTopInventory().equals(event.getInventory())) {
                    return;
                }
                controlService.markInventoryOpenBypass(controller);
                controller.openInventory(event.getInventory());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrop(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (controlService.isController(player) && isDropAction(event.getAction())) {
                event.setCancelled(true);
                if (event.getAction() == InventoryAction.DROP_ONE_CURSOR
                    || event.getAction() == InventoryAction.DROP_ALL_CURSOR) {
                    applyCursorDrop(player, event.getCursor(), event.getAction());
                }
                controlService.markTargetInventoryDirty(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (controlService.isController(player)) {
                controlService.markControllerInventoryDirty(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (controlService.isController(player)) {
                controlService.markControllerInventoryDirty(player);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (controlService.isController(damager)) {
                redirectControllerDamage(damager, event);
                return;
            }
        }
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (controlService.isController(victim)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (controlService.isController(player)
            && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            controlService.markCameraDirty(player);
        }
        if (controlService.isControlled(player)) {
            controlService.syncControllerToTarget(player, event.getTo());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (controlService.isController(player)) {
            controlService.markCameraDirty(player);
        }
        if (controlService.isControlled(player)) {
            controlService.syncControllerToTarget(player, event.getRespawnLocation());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (controlService.isController(player)) {
            controlService.markCameraDirty(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handleDisconnect(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        handleDisconnect(event.getPlayer());
    }

    private void handleDisconnect(Player player) {
        if (controlService.isController(player)) {
            controlService.stop(player, false);
        }
        if (controlService.isControlled(player)) {
            controlService.stopByTarget(player);
        }
    }

    private void cancelIfController(Player player, Cancellable event) {
        if (controlService.isController(player)) {
            event.setCancelled(true);
        }
    }

    private void redirectControllerDamage(Player controller, EntityDamageByEntityEvent event) {
        event.setCancelled(true);
        Player target = controlService.getTarget(controller);
        if (target == null) {
            controlService.stop(controller, false);
            return;
        }
        Entity victim = event.getEntity();
        if (victim.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        if (victim instanceof Player && controlService.isController((Player) victim)) {
            return;
        }
        target.attack(victim);
    }

    private boolean shouldBypassControllerCommand(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            return false;
        }
        String lower = trimmed.toLowerCase();
        return lower.equals("control")
            || lower.startsWith("control ")
            || lower.equals("fcontrolplayer:control")
            || lower.startsWith("fcontrolplayer:control ");
    }

    private boolean isDropAction(InventoryAction action) {
        if (action == null) {
            return false;
        }
        switch (action) {
            case DROP_ONE_CURSOR:
            case DROP_ALL_CURSOR:
            case DROP_ONE_SLOT:
            case DROP_ALL_SLOT:
                return true;
            default:
                return false;
        }
    }

    private void applyCursorDrop(Player player, ItemStack cursor, InventoryAction action) {
        if (cursor == null || cursor.getType().isAir()) {
            player.setItemOnCursor(null);
            return;
        }
        if (action == InventoryAction.DROP_ONE_CURSOR && cursor.getAmount() > 1) {
            ItemStack updated = cursor.clone();
            updated.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(updated);
            return;
        }
        player.setItemOnCursor(null);
    }
}
