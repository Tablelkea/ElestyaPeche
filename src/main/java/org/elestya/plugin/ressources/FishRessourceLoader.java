package org.elestya.plugin.ressources;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.elestya.plugin.sources.Fish;
import org.elestya.plugin.sources.FishRarity;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FishRessourceLoader {

    private final Plugin plugin;
    private final File fishFile;
    private YamlConfiguration fishConfig;
    private final Map<String, Fish> loadedFishes = new LinkedHashMap<>();

    public FishRessourceLoader(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fishFile = new File(plugin.getDataFolder(), "fish.yml");
        reloadFile();
    }

    public void reloadFile() {
        if (!fishFile.exists()) {
            plugin.saveResource("fish.yml", false);
        }
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(fishFile);
            fishConfig = configuration;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("fish.yml invalide : " + e.getMessage() + ". Les prises vanilla seront conservées.");
            fishConfig = new YamlConfiguration();
        }
    }

    public Optional<Fish> load(String id) {
        Objects.requireNonNull(id);

        String prefix = "fishes.";
        String name = fishConfig.getString(path(prefix + id, "name"));
        String rarityRaw = fishConfig.getString(path(prefix + id, "rarity"));
        String model = fishConfig.getString(path(prefix + id, "model"));
        String texture = fishConfig.getString(path(prefix + id, "texture"));

        if (name == null || rarityRaw == null || model == null || texture == null) {
            plugin.getLogger().warning("Poisson '" + id + "' : name, rarity, model ou texture manquant. Poisson vanilla utilisé à la place.");
            return Optional.empty();
        }

        try {
            FishRarity rarity = FishRarity.fromConfig(rarityRaw);
            if (NamespacedKey.fromString(model) == null || NamespacedKey.fromString(texture) == null) {
                throw new IllegalArgumentException("model et texture doivent être des identifiants namespacés valides");
            }
            double wmin = fishConfig.getDouble(path(prefix + id, "wmin"));
            double wmax = fishConfig.getDouble(path(prefix + id, "wmax"));

            Fish fish = new Fish(id, name, wmin, wmax, rarity, model, texture);
            loadedFishes.put(id, fish);
            return Optional.of(fish);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Poisson '" + id + "' invalide : " + e.getMessage() + ". Poisson vanilla utilisé à la place.");
            return Optional.empty();
        }
    }

    public void loadAll() {
        loadedFishes.clear();
        var section = fishConfig.getConfigurationSection("fishes");
        if (section == null) {
            plugin.getLogger().warning("Section 'fishes' absente de fish.yml. Les poissons vanilla seront conservés.");
            return;
        }
        for (String id : section.getKeys(false)) {
            load(id);
        }
        plugin.getLogger().info(loadedFishes.size() + " poisson(s) chargé(s)");
    }

    public Optional<Fish> get(String id) {
        return Optional.ofNullable(loadedFishes.get(id));
    }

    public Collection<Fish> getAll() {
        return Collections.unmodifiableCollection(loadedFishes.values());
    }

    private String path(String id, String value) {
        return id + "." + value;
    }
}
