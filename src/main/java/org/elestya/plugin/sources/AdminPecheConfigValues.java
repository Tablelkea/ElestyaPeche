package org.elestya.plugin.sources;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;


public final class AdminPecheConfigValues {
    private AdminPecheConfigValues() {}

public static boolean isRarityChancePath(String path) {
        return path.startsWith("rarity-chances.") && path.indexOf('.', "rarity-chances.".length()) < 0;
    }

public static Object parseEditedValue(Object oldValue, String input) {
        try {
            if (oldValue instanceof String) {
                return input;
            }
            if (oldValue instanceof Integer) {
                return Integer.parseInt(input);
            }
            if (oldValue instanceof Long) {
                return Long.parseLong(input);
            }
            if (oldValue instanceof Number) {
                double value = Double.parseDouble(input);
                return Double.isFinite(value) ? value : null;
            }
            if (oldValue instanceof Boolean) {
                if (input.equalsIgnoreCase("true")) {
                    return true;
                }
                if (input.equalsIgnoreCase("false")) {
                    return false;
                }
                return null;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return input;
    }

public static Object parseNewValue(String input) {
        String value = input.trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            try {
                double decimal = Double.parseDouble(value);
                if (Double.isFinite(decimal)) {
                    return decimal;
                }
            } catch (NumberFormatException ignoredDecimal) {
                return input;
            }
        }
        return input;
    }

private static double validChance(Object value) {
        if (!(value instanceof Number number)) {
            return 0;
        }
        double chance = number.doubleValue();
        return Double.isFinite(chance) && chance > 0 ? chance : 0;
    }

public static void balanceRarityChances(YamlConfiguration yaml, String changedPath, double changedValue) {
        ConfigurationSection chances = yaml.getConfigurationSection("rarity-chances");
        if (chances == null) {
            return;
        }
        String changedKey = changedPath.substring("rarity-chances.".length());
        List<String> otherKeys = chances.getKeys(false).stream()
                .filter(key -> !key.equals(changedKey))
                .sorted()
                .toList();
        if (otherKeys.isEmpty()) {
            return;
        }

        double remaining = 100 - changedValue;
        double otherTotal = otherKeys.stream()
                .mapToDouble(key -> validChance(chances.get(key)))
                .sum();
        double assigned = 0;
        for (int index = 0; index < otherKeys.size(); index++) {
            String key = otherKeys.get(index);
            double share;
            if (index == otherKeys.size() - 1) {
                share = remaining - assigned;
            } else if (otherTotal > 0) {
                share = remaining * validChance(chances.get(key)) / otherTotal;
            } else {
                share = remaining / otherKeys.size();
            }
            chances.set(key, share);
            assigned += share;
        }
    }
}
