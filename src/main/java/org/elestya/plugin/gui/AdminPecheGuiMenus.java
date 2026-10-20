package org.elestya.plugin.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.managers.FishingZoneManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


abstract class AdminPecheGuiMenus extends AdminPecheGuiViews {
    protected AdminPecheGuiMenus(JavaPlugin plugin, FishRessourceLoader fishLoader, PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager) { super(plugin, fishLoader, pecheConfiguration, fishingZoneManager); }

protected void handleBrowseClick(Player player, ConfigMenuHolder holder, int slot,
                                   InventoryClickEvent event) {
        int footerSlot = holder.pageSize;
        if (slot == footerSlot) {
            if (holder.path.isEmpty()) {
                openConfigSelection(player);
            } else {
                openSection(player, holder.configId, parentPath(holder.path), 0);
            }
            return;
        }
        if (slot == footerSlot + 1) {
            requestInput(player, new PendingInput(holder.configId, holder.path, true, null),
                    "Écris §fclé=valeur §7ou §fsection:nom§7 dans le chat. Écris §ccancel§7 pour annuler.");
            return;
        }
        if (slot == footerSlot + 2) {
            openSection(player, holder.configId, holder.path, holder.page);
            return;
        }
        if (slot == footerSlot + 3 && holder.page > 0) {
            openSection(player, holder.configId, holder.path, holder.page - 1);
            return;
        }
        if (slot == footerSlot + 5) {
            openSection(player, holder.configId, holder.path, holder.page + 1);
            return;
        }
        if (slot < 0 || slot >= holder.pageSize) {
            return;
        }

        ConfigFile file = configFiles.get(holder.configId);
        YamlConfiguration yaml = loadYaml(file);
        ConfigurationSection section = holder.path.isEmpty() ? yaml
                : yaml == null ? null : yaml.getConfigurationSection(holder.path);
        if (section == null) {
            sendError(player, "Impossible de lire cette section.");
            return;
        }
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(Comparator.naturalOrder());
        int index = holder.page * holder.pageSize + slot;
        if (index >= keys.size()) {
            return;
        }
        String key = keys.get(index);
        String path = holder.path.isEmpty() ? key : holder.path + "." + key;
        Object value = section.get(key);
        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            showValueHelp(player, holder.configId, path, value);
        } else if (event.isRightClick()) {
            openDeleteConfirmation(player, holder.configId, holder.path, path);
        } else if (value instanceof ConfigurationSection) {
            openSection(player, holder.configId, path, 0);
        } else if (value instanceof Boolean bool) {
            saveValue(player, holder.configId, path, !bool, holder.path, holder.page);
        } else {
            requestInput(player, new PendingInput(holder.configId, holder.path, false, path),
                    "Écris la nouvelle valeur dans le chat. Écris §ccancel§7 pour annuler.");
        }
    }

protected void openDeleteConfirmation(Player player, String configId, String parentPath, String path) {
        ConfigMenuHolder holder = new ConfigMenuHolder(this, MenuMode.CONFIRM_DELETE,
                configId, parentPath, 0, 0, path);
        Inventory inventory = createInventory(holder, 9, "§0§l✦ §4CONFIRMER LA SUPPRESSION");
        inventory.setItem(2, item(Material.RED_CONCRETE, "§c§lSupprimer §f" + lastSegment(path),
                List.of("§8Cette action est définitive", "§c▶ §fCliquer pour confirmer")));
        inventory.setItem(6, item(Material.LIME_CONCRETE, "§a§lAnnuler",
                List.of("§7Conserver le paramètre", "§a▶ §fCliquer pour revenir")));
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
            }
        }
        player.openInventory(inventory);
    }

protected void handleDeleteConfirmation(Player player, ConfigMenuHolder holder, int slot) {
        if (slot == 2) {
            YamlConfiguration yaml = loadYaml(configFiles.get(holder.configId));
            if (yaml == null || yaml.get(holder.targetPath) == null) {
                sendError(player, "Cette entrée n'existe plus.");
                openSection(player, holder.configId, holder.path, 0);
                return;
            }
            yaml.set(holder.targetPath, null);
            if (saveYaml(player, holder.configId, yaml)) {
                sendSuccess(player, "Entrée supprimée.");
            }
        }
        openSection(player, holder.configId, holder.path, 0);
    }
}
