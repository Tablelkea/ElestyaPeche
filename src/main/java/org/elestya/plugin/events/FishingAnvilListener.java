package org.elestya.plugin.events;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.elestya.plugin.enchantments.FishingEnchantments;

import java.util.Objects;

public final class FishingAnvilListener implements Listener {

    private final FishingEnchantments fishingEnchantments;

    public FishingAnvilListener(FishingEnchantments fishingEnchantments) {
        this.fishingEnchantments = Objects.requireNonNull(fishingEnchantments);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);
        ItemStack rod;
        ItemStack book;
        if (isRod(first) && fishingEnchantments.isCustomBook(second)) {
            rod = first;
            book = second;
        } else if (isRod(second) && fishingEnchantments.isCustomBook(first)) {
            rod = second;
            book = first;
        } else {
            return;
        }

        ItemStack result = fishingEnchantments.applyBook(rod, book);
        event.setResult(result);
        event.getView().setRepairCost(result == null ? 0 : 1);
    }

    private boolean isRod(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD;
    }
}
