package org.elestya.plugin.ressources;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.elestya.plugin.enchantments.FishingEnchantments;
import org.elestya.plugin.sources.Fish;
import org.elestya.plugin.sources.FishRarity;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class PecheConfiguration {

    private final Plugin plugin;
    private final File settingsFile;
    private final File rodFile;
    private final FishingEnchantments fishingEnchantments;
    private final NamespacedKey rodTierKey;
    private final Set<String> warnedSettings = new HashSet<>();
    private FileConfiguration settingsConfig;
    private FileConfiguration rodConfig;

    public PecheConfiguration(Plugin plugin, FishingEnchantments fishingEnchantments) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fishingEnchantments = Objects.requireNonNull(fishingEnchantments);
        this.settingsFile = new File(plugin.getDataFolder(), "settings.yml");
        this.rodFile = new File(plugin.getDataFolder(), "rod.yml");
        this.rodTierKey = new NamespacedKey(plugin, "rod_tier");
        reload();
    }

    public void reload() {
        warnedSettings.clear();
        settingsConfig = loadConfiguration(settingsFile, "settings.yml");
        rodConfig = loadConfiguration(rodFile, "rod.yml");
        fishingEnchantments.reload();
    }

    private FileConfiguration loadConfiguration(File file, String resourceName) {
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
            return configuration;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe(resourceName + " invalide : " + e.getMessage()
                    + ". Les prises vanilla seront conservées.");
            return new YamlConfiguration();
        }
    }

    public Optional<Fish> selectFish(Collection<Fish> fishes) {
        Map<FishRarity, Double> rarityChances = new EnumMap<>(FishRarity.class);
        double totalChance = 0;
        for (FishRarity rarity : FishRarity.values()) {
            double chance = getPercentage(settingsConfig, "rarity-chances." + rarity.name());
            if (chance > 0) {
                rarityChances.put(rarity, chance);
                totalChance += chance;
            }
        }
        if (totalChance <= 0 || totalChance > 100) {
            if (totalChance > 100) {
                warnSetting("settings.yml:rarity-chances",
                        "La somme des probabilités de rareté dépasse 100 %. Prises vanilla utilisées.");
            }
            return Optional.empty();
        }

        double roll = ThreadLocalRandom.current().nextDouble(100);
        for (Map.Entry<FishRarity, Double> entry : rarityChances.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) {
                List<Fish> eligibleFishes = fishes.stream()
                        .filter(fish -> fish.rarity() == entry.getKey())
                        .toList();
                if (eligibleFishes.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(eligibleFishes.get(ThreadLocalRandom.current().nextInt(eligibleFishes.size())));
            }
        }
        return Optional.empty();
    }

    public double getCustomFishChance(int rodTier, ItemStack rod) {
        double baseChance = getPercentage(settingsConfig, "custom-fish-chance");
        double tierBonus = getPercentage(rodConfig, "tiers." + rodTier + ".custom-fish-chance-bonus");
        return Math.min(100, baseChance + tierBonus + fishingEnchantments.getCustomFishChanceBonus(rod));
    }

    public double getWeightBias(ItemStack rod) {
        double bias = fishingEnchantments.getWeightBias(rod);
        return Double.isFinite(bias) ? Math.clamp(bias, 0, 1) : 0;
    }

    public int getHooksPerCatch(ItemStack rod) {
        return fishingEnchantments.getHooksPerCatch(rod);
    }

    public double getWeightMultiplier(int rodTier) {
        String path = rodTier == 0
                ? "default.weight-multiplier"
                : "tiers." + rodTier + ".weight-multiplier";
        if (!rodConfig.isDouble(path) && !rodConfig.isInt(path)) {
            warnSetting("rod.yml:" + path,
                    "Multiplicateur de poids absent ou invalide dans rod.yml. Prises vanilla utilisées.");
            return 0;
        }
        double multiplier = rodConfig.getDouble(path);
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            warnSetting("rod.yml:" + path,
                    "Le multiplicateur de poids doit être supérieur à 0. Prises vanilla utilisées.");
            return 0;
        }
        return multiplier;
    }

    public List<ItemStack> createFishingRods() {
        var tiers = rodConfig.getConfigurationSection("tiers");
        if (tiers == null) {
            warnSetting("rod.yml:tiers", "La section tiers manque dans rod.yml.");
            return List.of();
        }

        List<Integer> tierNumbers = new ArrayList<>();
        for (String key : tiers.getKeys(false)) {
            try {
                int tier = Integer.parseInt(key);
                if (tier > 0) {
                    tierNumbers.add(tier);
                }
            } catch (NumberFormatException e) {
                warnSetting("rod.yml:tiers." + key, "Tier de canne invalide ignoré : " + key);
            }
        }
        tierNumbers.sort(Integer::compareTo);

        List<ItemStack> rods = new ArrayList<>();
        for (int tier : tierNumbers) {
            String path = "tiers." + tier;
            String name = rodConfig.getString(path + ".name");
            String model = rodConfig.getString(path + ".item-model");
            if (name == null || name.isBlank()) {
                warnSetting("rod.yml:" + path, "Nom manquant pour la canne tier " + tier);
                continue;
            }

            ItemStack rod = new ItemStack(Material.FISHING_ROD);
            ItemMeta meta = rod.getItemMeta();
            meta.displayName(deserializeWithoutItalic(name));
            NamespacedKey itemModel = model == null || model.isBlank() ? null : NamespacedKey.fromString(model);
            if (itemModel != null) {
                meta.setItemModel(itemModel);
            } else {
                warnSetting("rod.yml:" + path + ".item-model",
                        "Modèle absent ou invalide pour le tier " + tier + " : canne vanilla utilisée.");
            }
            meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.INTEGER, tier);
            List<Component> lore = new ArrayList<>();
            lore.add(deserializeWithoutItalic("&7Palier de pêche : &f" + tier));
            lore.addAll(fishingEnchantments.applyConfigured(meta, tiers.getConfigurationSection(String.valueOf(tier))));
            meta.lore(lore);
            rod.setItemMeta(meta);
            rods.add(rod);
        }
        return List.copyOf(rods);
    }

    public Optional<Material> getCustomFishMaterial() {
        String materialName = settingsConfig.getString("custom-fish-item");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            warnSetting("settings.yml:custom-fish-item",
                    "custom-fish-item absent ou invalide dans settings.yml. Prises vanilla utilisées.");
            return Optional.empty();
        }
        return Optional.of(material);
    }

    public Component getFishDisplayName(Fish fish, double weight) {
        String template = settingsConfig.getString("messages.fish-name", "&f&l{name}");
        return deserializeWithoutItalic(format(template, fish, weight));
    }

    public List<Component> getFishLore(Fish fish, double weight) {
        List<Component> lore = new ArrayList<>();
        addFishLoreLine(lore, "messages.fish-rarity", fish, weight);
        addFishLoreLine(lore, "messages.fish-weight", fish, weight);
        return List.copyOf(lore);
    }

    private void addFishLoreLine(List<Component> lore, String path, Fish fish, double weight) {
        String template = settingsConfig.getString(path);
        if (template != null && !template.isBlank()) {
            lore.add(deserializeWithoutItalic(format(template, fish, weight)));
        }
    }

    private Component deserializeWithoutItalic(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }

    private String format(String template, Fish fish, double weight) {
        String rarityColor = settingsConfig.getString("rarity-colors." + fish.rarity().name(), "&7");
        String formattedWeight = BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString();
        return template
                .replace("{name}", fish.name())
                .replace("{rarity}", fish.rarity().name())
                .replace("{weight}", formattedWeight)
                .replace("{id}", fish.id())
                .replace("{rarity_color}", rarityColor);
    }

    private double getPercentage(FileConfiguration configuration, String path) {
        if (!configuration.isDouble(path) && !configuration.isInt(path)) {
            if (configuration.contains(path)) {
                warnSetting(path, path + " doit être un nombre entre 0 et 100. Réglage ignoré.");
            }
            return 0;
        }
        double value = configuration.getDouble(path);
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            warnSetting(path, path + " doit être un pourcentage entre 0 et 100. Réglage ignoré.");
            return 0;
        }
        return value;
    }

    private void warnSetting(String key, String message) {
        if (warnedSettings.add(key)) {
            plugin.getLogger().warning(message);
        }
    }
}
