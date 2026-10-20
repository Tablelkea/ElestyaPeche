package org.elestya.plugin.enchantments;

import org.bukkit.configuration.ConfigurationSection;

public final class MultiHameconEnchantment implements FishingEnchantment {

    @Override
    public String id() {
        return "multi_hamecon";
    }

    @Override
    public int getHooksPerCatch(ConfigurationSection level) {
        return level.getInt("hooks-per-catch", -1);
    }
}
