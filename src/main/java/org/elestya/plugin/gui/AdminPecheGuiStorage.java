package org.elestya.plugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.managers.FishingZoneManager;

import java.io.File;
import java.io.IOException;
import java.util.List;


abstract class AdminPecheGuiStorage extends AdminPecheGuiMenus {
    protected AdminPecheGuiStorage(JavaPlugin plugin, FishRessourceLoader fishLoader, PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager) { super(plugin, fishLoader, pecheConfiguration, fishingZoneManager); }
protected void saveAndReopen(Player player, String configId, YamlConfiguration yaml, String parentPath) {
        if (saveYaml(player, configId, yaml)) {
            reopen(player, configId, parentPath);
        }
    }

protected boolean saveYaml(Player player, String configId, YamlConfiguration yaml) {
        ConfigFile file = configFiles.get(configId);
        if (file == null) {
            sendError(player, "Configuration inconnue.");
            return false;
        }
        try {
            yaml.save(new File(plugin.getDataFolder(), file.fileName));
            reloadAllConfigurations();
            sendSuccess(player, file.displayName + " sauvegardé et rechargé.");
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder " + file.displayName + " : " + e.getMessage());
            sendError(player, "Erreur lors de la sauvegarde de " + file.displayName + ".");
            return false;
        }
    }

protected void reloadAllConfigurations() {
        fishLoader.reloadFile();
        fishLoader.loadAll();
        pecheConfiguration.reload();
        fishingZoneManager.reloadFromConfig();
        reloadConfigHelp();
    }

protected void reloadConfigHelp() {
        if (!helpFile.isFile()) {
            throw new IllegalStateException("Fichier d'aide absent : " + helpFile.getName());
        }
        try {
            configHelp = new YamlConfiguration();
            configHelp.load(helpFile);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Fichier d'aide invalide : " + exception.getMessage(), exception);
        }
    }

protected YamlConfiguration loadYaml(ConfigFile file) {
        if (file == null) {
            return null;
        }
        File source = new File(plugin.getDataFolder(), file.fileName);
        if (!source.isFile()) {
            plugin.getLogger().warning("Fichier de configuration absent : " + source.getName());
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(source);
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Configuration " + source.getName() + " invalide : " + e.getMessage());
            return null;
        }
    }

protected String findHelpPath(String configId, String path) {
        String root = "help." + configId + ".";
        String exact = root + path;
        if (configHelp.contains(exact)) {
            return exact;
        }
        String[] segments = path.split("\\.");
        for (int index = segments.length - 1; index >= 0; index--) {
            String[] pattern = segments.clone();
            pattern[index] = "*";
            String candidate = root + String.join(".", pattern);
            if (configHelp.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

protected void showValueHelp(Player player, String configId, String path, Object value) {
        sendAdminMessage(player, Component.text("-----------------------------", NamedTextColor.DARK_GRAY));
        sendAdminMessage(player, Component.text("Clé: ", NamedTextColor.GRAY, TextDecoration.BOLD)
                .append(Component.text(configId + "." + path, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)));
        sendAdminMessage(player, Component.text("Valeur: ", NamedTextColor.GRAY, TextDecoration.BOLD)
                .append(Component.text(String.valueOf(value), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false)));
        String helpPath = findHelpPath(configId, path);
        String description = value instanceof ConfigurationSection section
                ? "Section contenant " + section.getKeys(false).size() + " clé(s)."
                : helpPath == null ? null : configHelp.getString(helpPath + ".description");
        if (description != null && !description.isBlank()) {
            sendAdminMessage(player, Component.text("Description: ", NamedTextColor.GRAY, TextDecoration.BOLD)
                    .append(Component.text(description, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
        } else {
            sendAdminMessage(player, Component.text("Description: ", NamedTextColor.GRAY, TextDecoration.BOLD)
                    .append(Component.text("Aucune description disponible.", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false)));
        }
        List<String> options = value instanceof ConfigurationSection section
                ? section.getKeys(false).stream().sorted().toList()
                : helpPath == null ? List.of() : configHelp.getStringList(helpPath + ".options");
        sendAdminMessage(player, Component.text("Paramètres:", NamedTextColor.GRAY, TextDecoration.BOLD));
        if (!options.isEmpty()) {
            options.forEach(option -> sendAdminMessage(player,
                    Component.text("- ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(option, NamedTextColor.GOLD))));
        } else {
            sendAdminMessage(player, Component.text("- Aucun paramètre renseigné.", NamedTextColor.GOLD));
        }
        sendAdminMessage(player, Component.text("-----------------------------", NamedTextColor.GRAY));
    }
}
