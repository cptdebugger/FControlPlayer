package me.morok.fControlPlayer.control.inventory;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class InventorySnapshot {
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final int heldSlot;

    private InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, int heldSlot) {
        this.contents = contents;
        this.armor = armor;
        this.offhand = offhand;
        this.heldSlot = heldSlot;
    }

    public static InventorySnapshot capture(Player player) {
        PlayerInventory inventory = player.getInventory();
        return new InventorySnapshot(
            InventoryUtils.cloneItems(inventory.getContents()),
            InventoryUtils.cloneItems(inventory.getArmorContents()),
            InventoryUtils.cloneItem(inventory.getItemInOffHand()),
            inventory.getHeldItemSlot()
        );
    }

    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setContents(InventoryUtils.cloneItems(contents));
        inventory.setArmorContents(InventoryUtils.cloneItems(armor));
        inventory.setItemInOffHand(InventoryUtils.cloneItem(offhand));
        inventory.setHeldItemSlot(heldSlot);
        player.updateInventory();
    }
}
