package org.elestya.plugin.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.elestya.plugin.sources.AdminPecheConfigValues;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.managers.FishingZoneManager;

import java.util.UUID;


class AdminPecheGuiEditor extends AdminPecheGuiStorage {
    protected AdminPecheGuiEditor(JavaPlugin plugin, FishRessourceLoader fishLoader, PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager) { super(plugin, fishLoader, pecheConfiguration, fishingZoneManager); }
protected void handleChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingInput pending = pendingInputs.remove(playerId);
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> applyInput(player, pending, input));
    }

protected void handleQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

protected void requestInput(Player player, PendingInput input, String prompt) {
        pendingInputs.put(player.getUniqueId(), input);
        player.closeInventory();
        sendPrompt(player, prompt);
    }

protected void applyInput(Player player, PendingInput pending, String input) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.hasPermission(PERMISSION)) {
            pendingInputs.remove(player.getUniqueId());
            sendError(player, "Vous n'avez pas la permission de modifier ces fichiers.");
            return;
        }
        String text = input.trim();
        if (text.equalsIgnoreCase("cancel")) {
            sendInfo(player, "Modification annulée.");
            reopen(player, pending.configId, pending.parentPath);
            return;
        }
        ConfigFile file = configFiles.get(pending.configId);
        YamlConfiguration yaml = loadYaml(file);
        if (yaml == null) {
            sendError(player, "Impossible de lire " + file.displayName + ".");
            return;
        }

        if (pending.addEntry) {
            applyNewEntry(player, pending, text, yaml);
        } else {
            Object oldValue = yaml.get(pending.targetPath);
            if (oldValue == null || oldValue instanceof ConfigurationSection) {
                sendError(player, "Cette valeur n'existe plus ou n'est pas modifiable.");
                reopen(player, pending.configId, pending.parentPath);
                return;
            }
            Object parsed = AdminPecheConfigValues.parseEditedValue(oldValue, text);
            if (parsed == null) {
                sendError(player, "Valeur invalide pour le type "
                        + oldValue.getClass().getSimpleName() + ".");
                requestInput(player, pending, "Valeur invalide. Réessaie ou écris §ccancel§7.");
                return;
            }
            if (AdminPecheConfigValues.isRarityChancePath(pending.targetPath)) {
                if (!(parsed instanceof Number number)
                        || number.doubleValue() < 0 || number.doubleValue() > 100) {
                    sendError(player, "La probabilité d'une rareté doit être entre 0 et 100.");
                    requestInput(player, pending, "Valeur invalide. Entre un pourcentage de 0 à 100, ou §ccancel§7.");
                    return;
                }
                AdminPecheConfigValues.balanceRarityChances(yaml, pending.targetPath, number.doubleValue());
            }
            yaml.set(pending.targetPath, parsed);
            saveAndReopen(player, pending.configId, yaml, pending.parentPath);
        }
    }

protected void applyNewEntry(Player player, PendingInput pending, String input, YamlConfiguration yaml) {
        String parent = pending.parentPath;
        if (!parent.isEmpty() && yaml.getConfigurationSection(parent) == null) {
            sendError(player, "La section parente n'existe plus.");
            return;
        }
        if (input.regionMatches(true, 0, "section:", 0, "section:".length())) {
            String key = input.substring("section:".length()).trim();
            if (!validKey(key)) {
                sendError(player, "Nom de section invalide.");
                requestInput(player, pending, "Nom invalide. Réessaie ou écris §ccancel§7.");
                return;
            }
            String path = join(parent, key);
            if (yaml.contains(path)) {
                sendError(player, "Cette clé existe déjà.");
                requestInput(player, pending, "Clé déjà présente. Réessaie ou écris §ccancel§7.");
                return;
            }
            yaml.createSection(path);
        } else {
            int separator = input.indexOf('=');
            if (separator < 1) {
                sendError(player, "Format attendu : clé=valeur ou section:nom.");
                requestInput(player, pending, "Format invalide. Réessaie ou écris §ccancel§7.");
                return;
            }
            String key = input.substring(0, separator).trim();
            String rawValue = input.substring(separator + 1);
            if (!validKey(key)) {
                sendError(player, "Clé invalide. Utilise lettres, chiffres, tiret ou underscore.");
                requestInput(player, pending, "Clé invalide. Réessaie ou écris §ccancel§7.");
                return;
            }
            String path = join(parent, key);
            if (yaml.contains(path)) {
                sendError(player, "Cette clé existe déjà.");
                requestInput(player, pending, "Clé déjà présente. Réessaie ou écris §ccancel§7.");
                return;
            }
            Object value = AdminPecheConfigValues.parseNewValue(rawValue);
            if (AdminPecheConfigValues.isRarityChancePath(path)) {
                if (!(value instanceof Number number)
                        || number.doubleValue() < 0 || number.doubleValue() > 100) {
                    sendError(player, "La probabilité d'une rareté doit être entre 0 et 100.");
                    requestInput(player, pending, "Valeur invalide. Entre un pourcentage de 0 à 100, ou §ccancel§7.");
                    return;
                }
                AdminPecheConfigValues.balanceRarityChances(yaml, path, number.doubleValue());
            }
            yaml.set(path, value);
        }
        saveAndReopen(player, pending.configId, yaml, parent);
    }

protected void saveValue(Player player, String configId, String path, Object value,
                           String parentPath, int page) {
        YamlConfiguration yaml = loadYaml(configFiles.get(configId));
        if (yaml == null || yaml.get(path) == null) {
            sendError(player, "Impossible de modifier cette entrée.");
            return;
        }
        yaml.set(path, value);
        if (saveYaml(player, configId, yaml)) {
            openSection(player, configId, parentPath, page);
        }
    }

protected void reopen(Player player, String configId, String path) {
        if (player.isOnline()) {
            openSection(player, configId, path, 0);
        }
    }
}
