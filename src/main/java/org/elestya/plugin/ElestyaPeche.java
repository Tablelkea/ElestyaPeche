package org.elestya.plugin;

import org.elestya.plugin.commands.AdminPecheCommand;
import org.elestya.plugin.gui.AdminPecheGui;
import org.elestya.plugin.enchantments.FishingEnchantments;
import org.elestya.plugin.events.FishingAnvilListener;
import org.elestya.plugin.events.FishingEvent;
import org.elestya.plugin.managers.FishingManager;
import org.elestya.plugin.managers.FishingZoneManager;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ElestyaPeche extends JavaPlugin {

    private FishingEvent fishingEvent;
    private FishingManager manager;
    private FishingZoneManager fishingZoneManager;

    @Override
    public void onEnable() {
        FishRessourceLoader fishLoader = new FishRessourceLoader(this);
        fishLoader.loadAll();
        PecheConfiguration pecheConfiguration = new PecheConfiguration(this, new FishingEnchantments(this));
        getServer().getPluginManager().registerEvents(
                new FishingAnvilListener(pecheConfiguration.getFishingEnchantments()), this);
        fishingZoneManager = new FishingZoneManager(this);
        manager = new FishingManager(this, pecheConfiguration, fishLoader, fishingZoneManager);
        fishingEvent = new FishingEvent(this, manager);
        fishingZoneManager.reloadFromConfig();
        manager.clearAllVisualHooks();
        getServer().getPluginManager().registerEvents(
                fishingEvent, this);

        AdminPecheGui adminPecheGui = new AdminPecheGui(this, fishLoader, pecheConfiguration, fishingZoneManager);
        AdminPecheCommand adminPecheCommand = new AdminPecheCommand(
                pecheConfiguration, fishingZoneManager, adminPecheGui);
        var adminCommand = getCommand("adminpeche");
        if (adminCommand == null) {
            throw new IllegalStateException("La commande adminpeche est absente de plugin.yml");
        }
        adminCommand.setExecutor(adminPecheCommand);
        adminCommand.setTabCompleter(adminPecheCommand);
        getServer().getPluginManager().registerEvents(adminPecheGui, this);
    }

    @Override
    public void onDisable() {
        if (fishingEvent != null) {
            manager.clearAllVisualHooks();
            fishingEvent = null;
        }
        if (fishingZoneManager != null) {
            fishingZoneManager.shutdown();
            fishingZoneManager = null;
        }
    }
}
