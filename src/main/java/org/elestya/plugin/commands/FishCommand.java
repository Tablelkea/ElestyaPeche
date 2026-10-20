package org.elestya.plugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FishCommand implements CommandExecutor {

    private final FishRessourceLoader loader;
    private final PecheConfiguration pecheConfiguration;

    public FishCommand(FishRessourceLoader loader, PecheConfiguration pecheConfiguration) {
        this.loader = Objects.requireNonNull(loader);
        this.pecheConfiguration = Objects.requireNonNull(pecheConfiguration);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args
    ) {
        if (args.length != 1) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loader.reloadFile();
            loader.loadAll();
            pecheConfiguration.reload();

            sender.sendMessage("§afish.yml, settings.yml, rod.yml et enchant.yml rechargés : "
                    + loader.getAll().size() + " poisson(s) chargé(s).");
            return true;
        }
        if (args[0].equalsIgnoreCase("item")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cCette commande doit être exécutée par un joueur.");
                return true;
            }

            int given = 0;
            for (ItemStack rod : pecheConfiguration.createFishingRods()) {
                var leftovers = player.getInventory().addItem(rod);
                leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                given++;
            }
            player.sendMessage("§a" + given + " canne(s) de pêche reçue(s).");
            return true;
        }
        return false;
    }
}