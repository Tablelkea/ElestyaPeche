package org.elestya.plugin.enchantments;

import org.bukkit.configuration.ConfigurationSection;

public final class ChanceEnchantment implements FishingEnchantment {

    @Override
    public String id() {
        return "chance";
    }

    @Override
    public double getCustomFishChanceBonus(ConfigurationSection level) {
        return level.getDouble("custom-fish-chance-bonus");
    }
}
