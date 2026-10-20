package org.elestya.plugin.enchantments;

import org.bukkit.configuration.ConfigurationSection;

public interface FishingEnchantment {

    String id();

    default double getCustomFishChanceBonus(ConfigurationSection level) {
        return 0;
    }

    default double getWeightBias(ConfigurationSection level) {
        return 0;
    }

    default int getHooksPerCatch(ConfigurationSection level) {
        return 1;
    }
}
