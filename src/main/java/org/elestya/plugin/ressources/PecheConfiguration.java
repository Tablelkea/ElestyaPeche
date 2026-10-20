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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class PecheConfiguration {

    private final Plugin plugin;
    private final File settingsFile;
    private final File rodFile;
    private final FishingEnchantments fishingEnchantments;
    private final NamespacedKey rodTierKey;
    private final NamespacedKey rodMaxUsesKey;
    private final NamespacedKey rodUsesRemainingKey;
    private final Set<String> warnedSettings = new HashSet<>();
    private FileConfiguration settingsConfig;
    private FileConfiguration rodConfig;

    public PecheConfiguration(Plugin plugin, FishingEnchantments fishingEnchantments) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fishingEnchantments = Objects.requireNonNull(fishingEnchantments);
        this.settingsFile = new File(plugin.getDataFolder(), "settings.yml");
        this.rodFile = new File(plugin.getDataFolder(), "rod.yml");
        this.rodTierKey = new NamespacedKey(plugin, "rod_tier");
        this.rodMaxUsesKey = new NamespacedKey(plugin, "rod_max_uses");
        this.rodUsesRemainingKey = new NamespacedKey(plugin, "rod_uses_remaining");
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
        return selectFish(fishes, null);
    }

    public Optional<Fish> selectFish(Collection<Fish> fishes, FishRarity preferredRarity) {
        Map<FishRarity, Double> rarityChances = new EnumMap<>(FishRarity.class);
        double totalChance = 0;
        for (FishRarity rarity : FishRarity.values()) {
            double chance = getPercentage(settingsConfig, "rarity-chances." + rarity.name());
            if (rarity == preferredRarity) {
                chance *= 1 + rarity.ordinal() * 2;
            }
            if (chance > 0) {
                rarityChances.put(rarity, chance);
                totalChance += chance;
            }
        }
        if (totalChance <= 0) {
            return Optional.empty();
        }

        double roll = ThreadLocalRandom.current().nextDouble(Math.max(100, totalChance));
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

    public int getBiteWindowTicks() {
        return getIntegerSetting("fishing.bite-window-ticks", 60, 1, 1200);
    }

    public double getHookSpacing() {
        return getDecimalSetting("fishing.hook-spacing", 5.0, 0.1, 32.0);
    }

    public double getHookSpacingStep() {
        return getDecimalSetting("fishing.hook-spacing-step", 5.0, 0.0, 32.0);
    }

    public double getHookStagger() {
        return getDecimalSetting("fishing.hook-stagger", 5.0, -32.0, 32.0);
    }

    public boolean isFishingDebugEnabled() {
        String path = "fishing.debug";
        if (!settingsConfig.isBoolean(path)) {
            if (settingsConfig.contains(path)) {
                warnSetting("settings.yml:" + path,
                        path + " doit être un booléen. Valeur par défaut utilisée.");
            }
            return true;
        }
        return settingsConfig.getBoolean(path);
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

    public int getRodMaxUses(int rodTier) {
        String tierPath = "tiers." + rodTier + ".max-uses";
        String path = rodConfig.contains(tierPath) ? tierPath : "default.max-uses";
        int defaultValue = 100;
        if (!rodConfig.isInt(path)) {
            if (rodConfig.contains(path)) {
                warnSetting("rod.yml:" + path,
                        path + " doit être un entier positif ou nul. Valeur par défaut utilisée.");
            }
            return defaultValue;
        }
        int maxUses = rodConfig.getInt(path);
        if (maxUses < 0) {
            warnSetting("rod.yml:" + path,
                    path + " doit être un entier positif ou nul. Valeur par défaut utilisée.");
            return defaultValue;
        }
        return maxUses;
    }

    public void initializeRodUses(ItemMeta meta, int rodTier) {
        int maxUses = getRodMaxUses(rodTier);
        meta.getPersistentDataContainer().set(rodMaxUsesKey, PersistentDataType.INTEGER, maxUses);
        meta.getPersistentDataContainer().set(rodUsesRemainingKey, PersistentDataType.INTEGER, maxUses);
        applyRodLore(meta, rodTier, maxUses, maxUses);
    }

    public int getRodUsesRemaining(ItemStack rod, int rodTier) {
        if (rodTier < 1 || !rod.hasItemMeta()) {
            return -1;
        }
        ItemMeta meta = rod.getItemMeta();
        var data = meta.getPersistentDataContainer();
        Integer maxUses = data.get(rodMaxUsesKey, PersistentDataType.INTEGER);
        Integer remaining = data.get(rodUsesRemainingKey, PersistentDataType.INTEGER);
        if (maxUses == null || remaining == null) {
            initializeRodUses(meta, rodTier);
            rod.setItemMeta(meta);
            int configuredMax = getRodMaxUses(rodTier);
            return configuredMax == 0 ? -1 : configuredMax;
        }
        if (maxUses <= 0) {
            applyRodLore(meta, rodTier, 0, 0);
            rod.setItemMeta(meta);
            return -1;
        }
        remaining = Math.clamp(remaining, 0, maxUses);
        applyRodLore(meta, rodTier, remaining, maxUses);
        rod.setItemMeta(meta);
        return remaining;
    }

    /**
     * Consomme une utilisation après une prise. Renvoie -1 pour une canne illimitée,
     * -2 si elle était déjà épuisée, sinon le nombre restant.
     */
    public int consumeRodUse(ItemStack rod, int rodTier) {
        if (rodTier < 1 || !rod.hasItemMeta()) {
            return -1;
        }
        ItemMeta meta = rod.getItemMeta();
        var data = meta.getPersistentDataContainer();
        Integer maxUses = data.get(rodMaxUsesKey, PersistentDataType.INTEGER);
        Integer remaining = data.get(rodUsesRemainingKey, PersistentDataType.INTEGER);
        if (maxUses == null || remaining == null) {
            initializeRodUses(meta, rodTier);
            data = meta.getPersistentDataContainer();
            maxUses = data.get(rodMaxUsesKey, PersistentDataType.INTEGER);
            remaining = data.get(rodUsesRemainingKey, PersistentDataType.INTEGER);
        }
        if (maxUses == null || maxUses <= 0) {
            rod.setItemMeta(meta);
            return -1;
        }
        if (remaining == null || remaining <= 0) {
            return -2;
        }
        remaining--;
        data.set(rodUsesRemainingKey, PersistentDataType.INTEGER, remaining);
        applyRodLore(meta, rodTier, remaining, maxUses);
        rod.setItemMeta(meta);
        return remaining;
    }

    private void applyRodLore(ItemMeta meta, int rodTier, int remaining, int maxUses) {
        List<Component> lore = new ArrayList<>();
        lore.add(deserializeWithoutItalic("&7Palier de pêche : &f" + rodTier));
        String template = rodConfig.getString("lore",
                "&7Utilisations restantes : &f{uses}/{max-uses}");
        String useLine = template.replace("{uses}", maxUses <= 0 ? "∞" : Integer.toString(remaining))
                .replace("{remaining}", maxUses <= 0 ? "∞" : Integer.toString(remaining))
                .replace("{max-uses}", maxUses <= 0 ? "∞" : Integer.toString(maxUses))
                .replace("{maxUse}", maxUses <= 0 ? "∞" : Integer.toString(maxUses));
        lore.add(deserializeWithoutItalic(useLine));
        var tierSection = rodConfig.getConfigurationSection("tiers." + rodTier);
        lore.addAll(fishingEnchantments.applyConfigured(meta, tierSection));
        meta.lore(lore);
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
            initializeRodUses(meta, tier);
            rod.setItemMeta(meta);
            rods.add(rod);
        }
        return List.copyOf(rods);
    }

    public List<ItemStack> createFishingEnchantmentBooks() {
        return fishingEnchantments.createBooks();
    }

    public FishingEnchantments getFishingEnchantments() {
        return fishingEnchantments;
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
        String formattedWeightGrams = Long.toString(Math.round(weight * 1000));
        template = template.replace("{weight} kg", "{weight_g} g")
                .replace("{weight}kg", "{weight_g}g");
        return template
                .replace("{name}", fish.name())
                .replace("{rarity}", fish.rarity().name())
                .replace("{weight}", formattedWeight)
                .replace("{weight_g}", formattedWeightGrams)
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

    private int getIntegerSetting(String path, int defaultValue, int minimum, int maximum) {
        if (!settingsConfig.isInt(path)) {
            if (settingsConfig.contains(path)) {
                warnSetting("settings.yml:" + path,
                        path + " doit être un entier entre " + minimum + " et " + maximum
                                + ". Valeur par défaut utilisée.");
            }
            return defaultValue;
        }
        int value = settingsConfig.getInt(path);
        if (value < minimum || value > maximum) {
            warnSetting("settings.yml:" + path,
                    path + " doit être compris entre " + minimum + " et " + maximum
                            + ". Valeur par défaut utilisée.");
            return defaultValue;
        }
        return value;
    }

    private double getDecimalSetting(String path, double defaultValue, double minimum, double maximum) {
        if (!settingsConfig.isDouble(path) && !settingsConfig.isInt(path)) {
            if (settingsConfig.contains(path)) {
                warnSetting("settings.yml:" + path,
                        path + " doit être un nombre entre " + minimum + " et " + maximum
                                + ". Valeur par défaut utilisée.");
            }
            return defaultValue;
        }
        double value = settingsConfig.getDouble(path);
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            warnSetting("settings.yml:" + path,
                    path + " doit être compris entre " + minimum + " et " + maximum
                            + ". Valeur par défaut utilisée.");
            return defaultValue;
        }
        return value;
    }

    private void warnSetting(String key, String message) {
        if (warnedSettings.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    public double getSellPricePerKg(String rarityName) {
        String rarityPath = "vente.prix-par-rarete." + rarityName.toLowerCase(Locale.ROOT);
        double fallback = getNonNegativePrice("vente.prix-par-kg", 10.0);
        return getNonNegativePrice(rarityPath, fallback);
    }

    public double getSellBasePrice(String rarityName) {
        String path = "vente.prix-de-base-par-rarete." + rarityName.toLowerCase(Locale.ROOT);
        return getNonNegativePrice(path, 0.0);
    }

    public double getSellPrice(String rarityName, double weight) {
        if (!Double.isFinite(weight) || weight < 0) {
            return 0;
        }
        return getSellBasePrice(rarityName) + weight * getSellPricePerKg(rarityName);
    }

    private double getNonNegativePrice(String path, double defaultValue) {
        if (!settingsConfig.isDouble(path) && !settingsConfig.isInt(path)) {
            if (settingsConfig.contains(path)) {
                warnSetting("settings.yml:" + path,
                        path + " doit être un nombre positif ou nul. Valeur par défaut utilisée.");
            }
            return defaultValue;
        }
        double value = settingsConfig.getDouble(path);
        if (!Double.isFinite(value) || value < 0) {
            warnSetting("settings.yml:" + path,
                    path + " doit être un nombre positif ou nul. Valeur par défaut utilisée.");
            return defaultValue;
        }
        return value;
    }

    public String getCurrencySymbol() {
        return settingsConfig.getString("vente.symbole", "$");
    }
}
