package org.elestya.plugin.sources;

import java.util.Objects;

public record Fish(String id, String name, double wmin, double wmax, FishRarity rarity,
                   String model, String texture) {

    public Fish{
        Objects.requireNonNull(id);
        Objects.requireNonNull(rarity);
        Objects.requireNonNull(name);
        Objects.requireNonNull(model);
        Objects.requireNonNull(texture);
        if(id.isBlank()) throw new IllegalArgumentException("fish Id cannot be blank");
        if(name.isBlank()) throw new IllegalArgumentException("fish name cannot be blank");
        if(model.isBlank()) throw new IllegalArgumentException("fish model cannot be blank");
        if(texture.isBlank()) throw new IllegalArgumentException("fish texture cannot be blank");
        if (!Double.isFinite(wmin) || !Double.isFinite(wmax) || wmin <= 0) {
            throw new IllegalArgumentException("fish weights must be finite and greater than 0");
        }
        if (wmin > wmax) throw new IllegalArgumentException("fish min weight cannot be greater than is max");
    }

}
