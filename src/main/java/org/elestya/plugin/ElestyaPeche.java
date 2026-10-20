package org.elestya.plugin;

import org.elestya.plugin.commands.FishCommand;
import org.elestya.plugin.enchantments.FishingEnchantments;
import org.elestya.plugin.events.FishingEvent;
import org.elestya.plugin.managers.FishingManager;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElestyaPeche extends JavaPlugin {

    private FishingEvent fishingEvent;
    private FishingManager manager;

    @Override
    public void onEnable() {
        FishRessourceLoader fishLoader = new FishRessourceLoader(this);
        fishLoader.loadAll();
        PecheConfiguration pecheConfiguration = new PecheConfiguration(this, new FishingEnchantments(this));
        manager = new FishingManager(this, pecheConfiguration, fishLoader);
        fishingEvent = new FishingEvent(this, manager);
        manager.clearAllVisualHooks();
        getServer().getPluginManager().registerEvents(
                fishingEvent, this);

        var fishCommand = getCommand("fish");
        if (fishCommand == null) {
            throw new IllegalStateException("La commande fish est absente de plugin.yml");
        }
        fishCommand.setExecutor(new FishCommand(fishLoader, pecheConfiguration));
    }

    @Override
    public void onDisable() {
        if (fishingEvent != null) {
            manager.clearAllVisualHooks();
            fishingEvent = null;
        }
    }
}
