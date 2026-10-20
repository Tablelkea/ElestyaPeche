package org.elestya.plugin.enchantments;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private static final Pattern ENCHANTMENT_ID = Pattern.compile("[a-z0-9_-]+");
    private static final List<FishingEnchantment> ENCHANTMENTS = List.of(
            new ChanceEnchantment(),
            new GrosPoissonEnchantment(),
            new MultiHameconEnchantment()
    );

    private final Plugin plugin;
    private final File file;
    private final Set<String> warned = new HashSet<>();
    private FileConfiguration config;

    public FishingEnchantments(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.file = new File(plugin.getDataFolder(), "enchant.yml");
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

    public List<Component> applyConfigured(ItemMeta meta, ConfigurationSection rodTier) {
        if (rodTier == null) {
            return List.of();
        }
        ConfigurationSection configured = rodTier.getConfigurationSection("enchants");
        if (configured == null) {
            return List.of();
        }

        List<Component> lore = new ArrayList<>();
        for (String id : configured.getKeys(false)) {
            FishingEnchantment enchantment = getEnchantment(id);
            if (enchantment == null) {
                plugin.getLogger().warning("Identifiant d'enchantement invalide dans rod.yml : " + id);
                continue;
            }
            int level = configured.getInt(id, -1);
            String path = "enchants." + id;
            int maxLevel = config.getInt(path + ".max-level", 0);
            String name = config.getString(path + ".display-name");
            if (level < 1 || level > maxLevel || name == null) {
                plugin.getLogger().warning("Enchantement '" + id + "' ou niveau " + level
                        + " absent/invalide dans enchant.yml.");
                continue;
            }
            meta.getPersistentDataContainer().set(enchantmentKey(id), PersistentDataType.INTEGER, level);
            lore.add(noItalic(deserialize(name + " " + romanNumeral(level))));
        }
        return List.copyOf(lore);
    }

    public double getCustomFishChanceBonus(ItemStack rod) {
        double total = 0;
        for (FishingEnchantment enchantment : ENCHANTMENTS) {
            ConfigurationSection level = getLevelConfiguration(rod, enchantment);
            if (level != null) {
                total += validEffect(enchantment.id(), level, "custom-fish-chance-bonus",
                        enchantment.getCustomFishChanceBonus(level), 100);
            }
        }
        return total;
    }

    public double getWeightBias(ItemStack rod) {
        double total = 0;
        for (FishingEnchantment enchantment : ENCHANTMENTS) {
            ConfigurationSection level = getLevelConfiguration(rod, enchantment);
            if (level != null) {
                total += validEffect(enchantment.id(), level, "weight-bias",
                        enchantment.getWeightBias(level), 1);
            }
        }
        return total;
    }

    /**
     * Cumule les hameçons de tous les enchantements : on part de 1 et chaque enchantement
     * ajoute (valeur - 1), car la valeur par défaut de l'interface est 1.
     */
    public int getHooksPerCatch(ItemStack rod) {
        int hooks = 1;
        for (FishingEnchantment enchantment : ENCHANTMENTS) {
            ConfigurationSection level = getLevelConfiguration(rod, enchantment);
            if (level == null) {
                continue;
            }
            int value = enchantment.getHooksPerCatch(level);
            if (value < 1 || value > MAX_HOOKS) {
                String path = "enchants." + enchantment.id() + ".levels." + level.getName() + ".hooks-per-catch";
                warn(path, "Nombre d'hameçons invalide dans enchant.yml : " + path);
                continue;
            }
            hooks += value - 1;
        }
        return Math.min(hooks, MAX_HOOKS);
    }

    private ConfigurationSection getLevelConfiguration(ItemStack rod, FishingEnchantment enchantment) {
        if (enchantment == null || !rod.hasItemMeta()) {
            return null;
        }
        Integer level = rod.getItemMeta().getPersistentDataContainer()
                .get(enchantmentKey(enchantment.id()), PersistentDataType.INTEGER);
        if (level == null || level < 1) {
            return null;
        }
        String path = "enchants." + enchantment.id();
        if (level > config.getInt(path + ".max-level", 0)) {
            return null;
        }
        return config.getConfigurationSection(path + ".levels." + level);
    }

    private double validEffect(String id, ConfigurationSection level, String effect,
                               double value, double maximum) {
        String path = "enchants." + id + ".levels." + level.getName() + "." + effect;
        if (!Double.isFinite(value) || value < 0 || value > maximum) {
            warn(path, "Effet d'enchantement invalide dans enchant.yml : " + path);
            return 0;
        }
        return value;
    }

    private FishingEnchantment getEnchantment(String id) {
        if (!ENCHANTMENT_ID.matcher(id).matches()) {
            return null;
        }
        return ENCHANTMENTS.stream()
                .filter(enchantment -> enchantment.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private void warn(String path, String message) {
        if (warned.add(path)) {
            plugin.getLogger().warning(message);
        }
    }

    private NamespacedKey enchantmentKey(String id) {
        return new NamespacedKey(plugin, "enchant_" + id);
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
}