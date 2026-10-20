package org.elestya.plugin.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.managers.FishingZoneManager;


public class AdminPecheGui extends AdminPecheGuiEditor implements Listener {
    public AdminPecheGui(JavaPlugin plugin, FishRessourceLoader fishLoader, PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager) { super(plugin, fishLoader, pecheConfiguration, fishingZoneManager); reloadConfigHelp(); }
    public void openConfig(Player player) { pendingInputs.remove(player.getUniqueId()); openConfigSelection(player); }
    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder holder) || holder.owner != this) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission(PERMISSION) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        if (holder.mode == MenuMode.SELECT_CONFIG) handleConfigSelection(player, slot);
        else if (holder.mode == MenuMode.BROWSE) handleBrowseClick(player, holder, slot, event);
        else handleDeleteConfirmation(player, holder, slot);
    }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfigMenuHolder holder && holder.owner == this) event.setCancelled(true);
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { handleQuit(event); }
    @EventHandler public void onChat(AsyncChatEvent event) { handleChat(event); }
}
