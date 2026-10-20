package org.elestya.plugin.sources;

public enum FishRarity {

    BASIQUE,
    BRILLANT,
    EPIQUE,
    LEGENDAIRE,
    MYSTIQUE;

    public static FishRarity fromConfig(String value) {
        return switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "COMMON" -> BASIQUE;
            case "RARE" -> BRILLANT;
            case "EPIC" -> EPIQUE;
            case "MYTHIQUE", "CELESTE" -> MYSTIQUE;
            default -> valueOf(value.toUpperCase(java.util.Locale.ROOT));
        };
    }

}
