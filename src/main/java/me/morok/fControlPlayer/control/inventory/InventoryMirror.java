package me.morok.fControlPlayer.control.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

public final class InventoryMirror {
    public void syncToController(Player target, Player controller) {
        syncToController(target, controller, true);
    }

    public void syncToController(Player target, Player controller, boolean syncHeldSlot) {
        PlayerInventory source = target.getInventory();
        PlayerInventory dest = controller.getInventory();
        dest.setContents(InventoryUtils.cloneItems(source.getContents()));
        dest.setArmorContents(InventoryUtils.cloneItems(source.getArmorContents()));
        dest.setItemInOffHand(InventoryUtils.cloneItem(source.getItemInOffHand()));
        if (syncHeldSlot) {
            dest.setHeldItemSlot(source.getHeldItemSlot());
        }
        controller.updateInventory();
    }

    public void syncToTarget(Player controller, Player target) {
        PlayerInventory source = controller.getInventory();
        PlayerInventory dest = target.getInventory();
        dest.setContents(InventoryUtils.cloneItems(source.getContents()));
        dest.setArmorContents(InventoryUtils.cloneItems(source.getArmorContents()));
        dest.setItemInOffHand(InventoryUtils.cloneItem(source.getItemInOffHand()));
        dest.setHeldItemSlot(source.getHeldItemSlot());
        target.updateInventory();
    }
}
