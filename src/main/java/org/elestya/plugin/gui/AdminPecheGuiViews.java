package org.elestya.plugin.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.managers.FishingZoneManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


abstract class AdminPecheGuiViews extends AdminPecheGuiBase {
    protected AdminPecheGuiViews(JavaPlugin plugin, FishRessourceLoader fishLoader, PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager) { super(plugin, fishLoader, pecheConfiguration, fishingZoneManager); }

protected void openConfigSelection(Player player) {
        ConfigMenuHolder holder = new ConfigMenuHolder(this, MenuMode.SELECT_CONFIG, null, "", 0, 0, "");
        Inventory inventory = createInventory(holder, 9, "§0§l✦ §3ADMIN PÊCHE §8| §bCONFIG");
        int[] slots = {0, 1, 2, 3};
        for (int index = 0; index < configOrder.size(); index++) {
            ConfigFile file = configFiles.get(configOrder.get(index));
            inventory.setItem(slots[index], item(file.icon,
                    "§b§l" + file.displayName,
                    List.of("§8Fichier de configuration", "§7Modifier les paramètres associés",
                            "§e▶ §fCliquer pour ouvrir")));
        }
        inventory.setItem(5, item(Material.CLOCK, "§a§lRecharger les configurations",
                List.of("§8Synchronisation des fichiers", "§7Relire fish.yml, settings.yml,",
                        "§7rod.yml et enchant.yml", "§e▶ §fCliquer pour recharger")));
        inventory.setItem(8, item(Material.BARRIER, "§c§lFermer",
                List.of("§7Quitter ce menu", "§e▶ §fCliquer pour fermer")));
        for (int slot : new int[]{4, 6, 7}) {
            inventory.setItem(slot, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }
        player.openInventory(inventory);
    }

protected void handleConfigSelection(Player player, int slot) {
        if (slot == 8) {
            player.closeInventory();
            return;
        }
        if (slot == 5) {
            reloadAllConfigurations();
            sendSuccess(player, "Configurations de pêche rechargées.");
            openConfigSelection(player);
            return;
        }
        int[] slots = {0, 1, 2, 3};
        for (int index = 0; index < configOrder.size(); index++) {
            if (slots[index] == slot) {
                openSection(player, configOrder.get(index), "", 0);
                return;
            }
        }
    }

protected void openSection(Player player, String configId, String path, int page) {
        ConfigFile file = configFiles.get(configId);
        if (file == null) {
            sendError(player, "Configuration inconnue.");
            return;
        }
        YamlConfiguration yaml = loadYaml(file);
        if (yaml == null) {
            player.closeInventory();
            sendError(player, "Impossible de lire " + file.displayName + ".");
            return;
        }
        ConfigurationSection section = path.isEmpty() ? yaml : yaml.getConfigurationSection(path);
        if (section == null) {
            sendError(player, "Cette section n'existe plus.");
            openSection(player, configId, parentPath(path), 0);
            return;
        }

        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort(Comparator.naturalOrder());
        int pageSize = Math.min(MAX_ENTRY_SLOTS, Math.max(9, ((Math.max(1, keys.size()) + 8) / 9) * 9));
        int pageCount = Math.max(1, (keys.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int entryRows = pageSize / 9;
        int inventorySize = (entryRows + 1) * 9;
        int footerSlot = entryRows * 9;
        ConfigMenuHolder holder = new ConfigMenuHolder(
                this, MenuMode.BROWSE, configId, path, page, pageSize, "");
        String title = "§0§l✦ §3" + file.displayName
                + (path.isEmpty() ? "" : " §8» §b" + path);
        Inventory inventory = createInventory(holder, inventorySize, title);
        int start = page * pageSize;
        for (int index = start; index < Math.min(start + pageSize, keys.size()); index++) {
            String key = keys.get(index);
            String childPath = path.isEmpty() ? key : path + "." + key;
            Object value = section.get(key);
            boolean subsection = value instanceof ConfigurationSection;
            List<String> lore = new ArrayList<>();
            lore.add("§a" + childPath);
            if (subsection) {
                lore.add("§7Contient §b" + ((ConfigurationSection) value).getKeys(false).size()
                        + " §7paramètre(s)");
                lore.add("§a▶ §fClic gauche §8» §7Ouvrir");
            } else {
                lore.add("§7Valeur actuelle : §6" + displayValue(value));
                lore.add("§a▶ §fClic gauche §8» §7" + (value instanceof Boolean ? "Basculer" : "Modifier"));
            }
            lore.add("§c▶ §fClic droit §8» §cSupprimer");
            lore.add(subsection
                    ? "§b▶ §fQ §8» §7Afficher les clés"
                    : "§b▶ §fQ §8» §7Afficher la valeur et l'aide");
            Material icon = subsection ? Material.CHEST : value instanceof Boolean
                    ? ((Boolean) value ? Material.LIME_DYE : Material.GRAY_DYE) : Material.PAPER;
            inventory.setItem(index - start, item(icon, "§a" + key, lore));
        }

        inventory.setItem(footerSlot, item(Material.ARROW, "§e§lRetour",
                List.of("§7" + (path.isEmpty() ? "Revenir au choix des fichiers" : "Remonter d'une section"),
                        "§e▶ §fCliquer pour revenir")));
        inventory.setItem(footerSlot + 1, item(Material.WRITABLE_BOOK, "§a§lAjouter un paramètre",
                List.of("§7Créer une clé ou une section", "§fclé=valeur", "§fsection:nom",
                        "§e▶ §fCliquer puis saisir dans le chat")));
        inventory.setItem(footerSlot + 2, item(Material.CLOCK, "§b§lActualiser",
                List.of("§7Relire ce fichier depuis le disque", "§e▶ §fCliquer pour actualiser")));
        if (page > 0) {
            inventory.setItem(footerSlot + 3, item(Material.PAPER, "§e§lPage précédente",
                    List.of("§7Naviguer vers la page précédente", "§e▶ §fCliquer pour continuer")));
        }
        if (page + 1 < pageCount) {
            inventory.setItem(footerSlot + 5, item(Material.PAPER, "§e§lPage suivante",
                    List.of("§7Naviguer vers la page suivante", "§e▶ §fCliquer pour continuer")));
        }
        for (int footer = footerSlot; footer < inventorySize; footer++) {
            if (inventory.getItem(footer) == null) {
                inventory.setItem(footer, item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
            }
        }
        player.openInventory(inventory);
    }
}
