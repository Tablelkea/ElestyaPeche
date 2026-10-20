package org.elestya.plugin.enchantments;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class FishingEnchantments {

    private static final int MAX_HOOKS = 5;
    private static final int MAX_ENCHANTMENT_LEVEL = 255;
    private static final Pattern ENCHANTMENT_ID = Pattern.compile("[a-z0-9_-]+");

    private final Plugin plugin;
    private final File file;
    private final Set<String> warned = new HashSet<>();
    private final NamespacedKey bookIdKey;
    private final NamespacedKey bookLevelKey;
    private FileConfiguration config;

    public FishingEnchantments(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.file = new File(plugin.getDataFolder(), "enchant.yml");
        bookIdKey = new NamespacedKey(plugin, "custom_enchant_book_id");
        bookLevelKey = new NamespacedKey(plugin, "custom_enchant_book_level");
        reload();
    }

    public void reload() {
        warned.clear();
        if (!file.exists()) {
            plugin.saveResource("enchant.yml", false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            config = loaded;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("enchant.yml invalide : " + e.getMessage()
                    + ". Les cannes seront créées sans enchantements custom.");
            config = new YamlConfiguration();
        }
    }

    public List<ItemStack> createBooks() {
        ConfigurationSection enchantments = config.getConfigurationSection("enchants");
        if (enchantments == null) {
            return List.of();
        }
        List<ItemStack> books = new ArrayList<>();
        for (String id : enchantments.getKeys(false)) {
            if (!isValidEnchantment(id)) {
                warn("enchants." + id, "Identifiant d'enchantement invalide ignoré : " + id);
                continue;
            }
            int maxLevel = config.getInt("enchants." + id + ".max-level", 0);
            if (maxLevel < 1 || maxLevel > MAX_ENCHANTMENT_LEVEL) {
                warn("enchants." + id + ".max-level",
                        "Niveau maximal invalide pour l'enchantement '" + id + "' (1 à "
                                + MAX_ENCHANTMENT_LEVEL + ").");
                continue;
            }
            for (int level = 1; level <= maxLevel; level++) {
                if (getLevelConfiguration(id, level) == null) {
                    continue;
                }
                books.add(createBook(id, level));
            }
        }
        return List.copyOf(books);
    }

    public boolean isCustomBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) {
            return false;
        }
        var data = item.getItemMeta().getPersistentDataContainer();
        String id = data.get(bookIdKey, PersistentDataType.STRING);
        Integer level = data.get(bookLevelKey, PersistentDataType.INTEGER);
        return id != null && level != null && getLevelConfiguration(id, level) != null;
    }

    public ItemStack applyBook(ItemStack rod, ItemStack book) {
        if (rod == null || rod.getType() != Material.FISHING_ROD || !rod.hasItemMeta()
                || !isCustomBook(book)) {
            return null;
        }
        var bookData = book.getItemMeta().getPersistentDataContainer();
        String id = bookData.get(bookIdKey, PersistentDataType.STRING);
        Integer bookLevel = bookData.get(bookLevelKey, PersistentDataType.INTEGER);
        if (id == null || bookLevel == null) {
            return null;
        }

        ItemStack result = rod.clone();
        ItemMeta meta = result.getItemMeta();
        var rodData = meta.getPersistentDataContainer();
        NamespacedKey enchantmentKey = enchantmentKey(id);
        Integer currentLevel = rodData.get(enchantmentKey, PersistentDataType.INTEGER);
        int maxLevel = config.getInt("enchants." + id + ".max-level", 0);
        int targetLevel;
        if (currentLevel == null || currentLevel < 1) {
            targetLevel = bookLevel;
        } else if (currentLevel < bookLevel) {
            targetLevel = bookLevel;
        } else if (currentLevel.equals(bookLevel) && currentLevel < maxLevel) {
            targetLevel = currentLevel + 1;
        } else {
            return null;
        }
        rodData.set(enchantmentKey, PersistentDataType.INTEGER, targetLevel);

        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        Set<String> customLore = getAllEnchantLoreText();
        lore.removeIf(line -> customLore.contains(PlainTextComponentSerializer.plainText().serialize(line)));
        lore.addAll(getAppliedEnchantLore(meta));
        meta.lore(lore);
        result.setItemMeta(meta);
        return result;
    }

    public List<Component> applyConfigured(ItemMeta meta, ConfigurationSection rodTier) {
        if (rodTier != null) {
            ConfigurationSection configured = rodTier.getConfigurationSection("enchants");
            if (configured != null) {
                for (String id : configured.getKeys(false)) {
                    if (!isValidEnchantment(id)) {
                        warn("rod.yml:" + id, "Identifiant d'enchantement invalide dans rod.yml : " + id);
                        continue;
                    }
                    int level = configured.getInt(id, -1);
                    if (getLevelConfiguration(id, level) == null) {
                        warn("rod.yml:enchants." + id,
                                "Enchantement '" + id + "' ou niveau " + level + " absent/invalide dans enchant.yml.");
                        continue;
                    }
                    meta.getPersistentDataContainer().set(enchantmentKey(id), PersistentDataType.INTEGER, level);
                }
            }
        }
        return getAppliedEnchantLore(meta);
    }

    public double getCustomFishChanceBonus(ItemStack rod) {
        double[] total = {0};
        forEachAppliedLevel(rod, (id, level) -> {
            double value = getFiniteValue(level, "custom-fish-chance-bonus", 0);
            if (value < 0 || value > 100) {
                warnEffect(id, level, "custom-fish-chance-bonus");
            } else {
                total[0] += value;
            }
        });
        return total[0];
    }

    public double getWeightBias(ItemStack rod) {
        double[] total = {0};
        forEachAppliedLevel(rod, (id, level) -> {
            double value = getFiniteValue(level, "weight-bias", 0);
            if (value < 0 || value > 1) {
                warnEffect(id, level, "weight-bias");
            } else {
                total[0] += value;
            }
        });
        return total[0];
    }

    public int getHooksPerCatch(ItemStack rod) {
        int[] hooks = {1};
        forEachAppliedLevel(rod, (id, level) -> {
            if (!level.contains("hooks-per-catch")) {
                return;
            }
            int value = level.getInt("hooks-per-catch", -1);
            if (value < 1 || value > MAX_HOOKS) {
                warnEffect(id, level, "hooks-per-catch");
                return;
            }
            hooks[0] = Math.min(MAX_HOOKS, hooks[0] + value - 1);
        });
        return hooks[0];
    }

    private void forEachAppliedLevel(ItemStack rod, AppliedLevelConsumer consumer) {
        if (rod == null || !rod.hasItemMeta()) {
            return;
        }
        ConfigurationSection enchantments = config.getConfigurationSection("enchants");
        if (enchantments == null) {
            return;
        }
        var data = rod.getItemMeta().getPersistentDataContainer();
        for (String id : enchantments.getKeys(false)) {
            if (!isValidEnchantment(id)) {
                continue;
            }
            Integer level = data.get(enchantmentKey(id), PersistentDataType.INTEGER);
            ConfigurationSection levelConfig = level == null ? null : getLevelConfiguration(id, level);
            if (levelConfig != null) {
                consumer.accept(id, levelConfig);
            }
        }
    }

    private List<Component> getAppliedEnchantLore(ItemMeta meta) {
        List<Component> lore = new ArrayList<>();
        ConfigurationSection enchantments = config.getConfigurationSection("enchants");
        if (enchantments == null) {
            return lore;
        }
        var data = meta.getPersistentDataContainer();
        for (String id : enchantments.getKeys(false)) {
            if (!isValidEnchantment(id)) {
                continue;
            }
            Integer level = data.get(enchantmentKey(id), PersistentDataType.INTEGER);
            if (level == null || getLevelConfiguration(id, level) == null) {
                continue;
            }
            String name = config.getString("enchants." + id + ".display-name", id);
            lore.add(noItalic(deserialize(name + " " + romanNumeral(level))));
        }
        return List.copyOf(lore);
    }

    private Set<String> getAllEnchantLoreText() {
        Set<String> lore = new HashSet<>();
        ConfigurationSection enchantments = config.getConfigurationSection("enchants");
        if (enchantments == null) {
            return lore;
        }
        for (String id : enchantments.getKeys(false)) {
            if (!isValidEnchantment(id)) {
                continue;
            }
            String name = config.getString("enchants." + id + ".display-name", id);
            int maxLevel = config.getInt("enchants." + id + ".max-level", 0);
            for (int level = 1; level <= maxLevel; level++) {
                if (getLevelConfiguration(id, level) != null) {
                    lore.add(PlainTextComponentSerializer.plainText()
                            .serialize(deserialize(name + " " + romanNumeral(level))));
                }
            }
        }
        return lore;
    }

    private ItemStack createBook(String id, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        String name = config.getString("enchants." + id + ".display-name", id);
        meta.displayName(noItalic(deserialize(name + " " + romanNumeral(level))));
        ConfigurationSection levelConfig = getLevelConfiguration(id, level);
        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(Component.text("Livre d'enchantement de pêche")));
        if (levelConfig != null) {
            if (levelConfig.contains("custom-fish-chance-bonus")) {
                lore.add(noItalic(Component.text("Chance de poisson custom : +"
                        + levelConfig.getDouble("custom-fish-chance-bonus") + "%")));
            }
            if (levelConfig.contains("weight-bias")) {
                lore.add(noItalic(Component.text("Biais de poids : "
                        + levelConfig.getDouble("weight-bias"))));
            }
            if (levelConfig.contains("hooks-per-catch")) {
                lore.add(noItalic(Component.text("Hameçons par prise : "
                        + levelConfig.getInt("hooks-per-catch"))));
            }
        }
        lore.add(noItalic(Component.text("À appliquer sur une canne avec une enclume")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(bookIdKey, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(bookLevelKey, PersistentDataType.INTEGER, level);
        book.setItemMeta(meta);
        return book;
    }

    private ConfigurationSection getLevelConfiguration(String id, int level) {
        int maxLevel = isValidEnchantment(id)
                ? config.getInt("enchants." + id + ".max-level", 0) : 0;
        if (level < 1 || level > maxLevel || maxLevel > MAX_ENCHANTMENT_LEVEL) {
            return null;
        }
        return config.getConfigurationSection("enchants." + id + ".levels." + level);
    }

    private boolean isValidEnchantment(String id) {
        return id != null && ENCHANTMENT_ID.matcher(id).matches()
                && config.getConfigurationSection("enchants." + id) != null;
    }

    private NamespacedKey enchantmentKey(String id) {
        return new NamespacedKey(plugin, "enchant_" + id);
    }

    private double getFiniteValue(ConfigurationSection level, String key, double fallback) {
        double value = level.getDouble(key, fallback);
        return Double.isFinite(value) ? value : Double.NaN;
    }

    private void warnEffect(String id, ConfigurationSection level, String effect) {
        String path = "enchants." + id + ".levels." + level.getName() + "." + effect;
        warn(path, "Effet d'enchantement invalide dans enchant.yml : " + path);
    }

    private void warn(String path, String message) {
        if (warned.add(path)) {
            plugin.getLogger().warning(message);
        }
    }

    private Component deserialize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private String romanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(level);
        };
    }

    @FunctionalInterface
    private interface AppliedLevelConsumer {
        void accept(String id, ConfigurationSection level);
    }
}
