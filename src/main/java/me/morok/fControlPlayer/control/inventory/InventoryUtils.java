package me.morok.fControlPlayer.control.inventory;

import org.bukkit.inventory.ItemStack;

final class InventoryUtils {
    private InventoryUtils() {
    }

    static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    static ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = cloneItem(items[i]);
        }
        return copy;
    }
}
