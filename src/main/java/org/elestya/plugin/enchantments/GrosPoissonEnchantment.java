package org.elestya.plugin.enchantments;

import org.bukkit.configuration.ConfigurationSection;

public final class GrosPoissonEnchantment implements FishingEnchantment {

    @Override
    public String id() {
        return "gros_poisson";
    }

    @Override
    public double getWeightBias(ConfigurationSection level) {
        return level.getDouble("weight-bias");
    }
}
