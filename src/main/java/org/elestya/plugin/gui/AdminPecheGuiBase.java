package org.elestya.plugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.managers.FishingZoneManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


abstract class AdminPecheGuiBase {
protected static final int MAX_ENTRY_SLOTS = 45;
    protected static final String PERMISSION = "elestyapeche.admin";

    protected final JavaPlugin plugin;
    protected final FishRessourceLoader fishLoader;
    protected final PecheConfiguration pecheConfiguration;
    protected final FishingZoneManager fishingZoneManager;
    protected final File helpFile;
    protected YamlConfiguration configHelp;
    protected final List<String> configOrder = List.of("settings", "fish", "rod", "enchant");
    protected final Map<String, ConfigFile> configFiles = Map.of(
            "settings", new ConfigFile("settings.yml", "settings.yml", Material.COMPARATOR),
            "fish", new ConfigFile("fish.yml", "fish.yml", Material.COD),
            "rod", new ConfigFile("rod.yml", "rod.yml", Material.FISHING_ROD),
            "enchant", new ConfigFile("enchant.yml", "enchant.yml", Material.ENCHANTED_BOOK)
    );
    protected final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

protected AdminPecheGuiBase(JavaPlugin plugin, FishRessourceLoader fishLoader,
                         PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fishLoader = Objects.requireNonNull(fishLoader);
        this.pecheConfiguration = Objects.requireNonNull(pecheConfiguration);
        this.fishingZoneManager = Objects.requireNonNull(fishingZoneManager);
        this.helpFile = new File(plugin.getDataFolder(), "config-help.yml");
        if (!helpFile.exists()) {
            plugin.saveResource("config-help.yml", false);
        }

    }

protected void sendInfo(CommandSender sender, String message) {
        sendAdminMessage(sender, Component.text(message, NamedTextColor.GRAY));
    }

protected void sendSuccess(CommandSender sender, String message) {
        sendAdminMessage(sender, Component.text(message, NamedTextColor.GREEN));
    }

protected void sendError(CommandSender sender, String message) {
        sendAdminMessage(sender, Component.text(message, NamedTextColor.RED));
    }

protected void sendPrompt(CommandSender sender, String message) {
        Component prompt = LegacyComponentSerializer.legacySection().deserialize(message);
        sendAdminMessage(sender, prompt);
    }

protected void sendAdminMessage(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

protected Inventory createInventory(ConfigMenuHolder holder, int size, String title) {
        Component titleComponent = LegacyComponentSerializer.legacySection().deserialize(title)
                .decoration(TextDecoration.ITALIC, false);
        Inventory inventory = plugin.getServer().createInventory(holder, size,
                titleComponent);
        holder.inventory = inventory;
        return inventory;
    }

protected ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        meta.displayName(serializer.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> styledLore = new ArrayList<>();
        if (!lore.isEmpty()) {
            styledLore.add(Component.text("━━━━━━━━━━━━━━━━", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        styledLore.addAll(lore.stream()
                .map(line -> serializer.deserialize(line).decoration(TextDecoration.ITALIC, false))
                .toList());
        meta.lore(styledLore);
        stack.setItemMeta(meta);
        return stack;
    }

protected String displayValue(Object value) {
        String text = String.valueOf(value);
        return text.length() > 45 ? text.substring(0, 42) + "..." : text;
    }

protected boolean validKey(String key) {
        return key.matches("[A-Za-z0-9_-]+");
    }

protected String parentPath(String path) {
        int separator = path.lastIndexOf('.');
        return separator < 0 ? "" : path.substring(0, separator);
    }

protected String lastSegment(String path) {
        int separator = path.lastIndexOf('.');
        return separator < 0 ? path : path.substring(separator + 1);
    }

protected String join(String parent, String child) {
        return parent.isEmpty() ? child : parent + "." + child;
    }

    protected abstract void requestInput(Player player, PendingInput input, String prompt);
    protected abstract void showValueHelp(Player player, String configId, String path, Object value);
    protected abstract void saveValue(Player player, String configId, String path, Object value, String parentPath, int page);
    protected abstract void reopen(Player player, String configId, String path);
    protected abstract void reloadAllConfigurations();
    protected abstract YamlConfiguration loadYaml(ConfigFile file);
    protected abstract boolean saveYaml(Player player, String configId, YamlConfiguration yaml);


    protected enum MenuMode { SELECT_CONFIG, BROWSE, CONFIRM_DELETE }

    protected static final class ConfigFile {
        protected final String displayName;
        protected final String fileName;
        protected final Material icon;
        protected ConfigFile(String displayName, String fileName, Material icon) {
            this.displayName = displayName;
            this.fileName = fileName;
            this.icon = icon;
        }
    }

    protected static final class PendingInput {
        protected final String configId;
        protected final String parentPath;
        protected final boolean addEntry;
        protected final String targetPath;
        protected PendingInput(String configId, String parentPath, boolean addEntry, String targetPath) {
            this.configId = configId;
            this.parentPath = parentPath;
            this.addEntry = addEntry;
            this.targetPath = targetPath;
        }
    }

    protected static final class ConfigMenuHolder implements InventoryHolder {
        protected final AdminPecheGuiBase owner;
        protected final MenuMode mode; protected final String configId; protected final String path;
        protected final int page; protected final int pageSize; protected final String targetPath;
        protected Inventory inventory;
        protected ConfigMenuHolder(AdminPecheGuiBase owner, MenuMode mode, String configId, String path, int page, int pageSize, String targetPath) {
            this.owner = owner; this.mode = mode; this.configId = configId; this.path = path;
            this.page = page; this.pageSize = pageSize; this.targetPath = targetPath;
        }
        @Override public @NotNull Inventory getInventory() { return Objects.requireNonNull(inventory); }
    }

}
