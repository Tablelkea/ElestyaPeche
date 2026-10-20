package org.elestya.plugin.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.elestya.plugin.gui.AdminPecheGui;
import org.elestya.plugin.managers.FishingZoneManager;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AdminPecheCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "elestyapeche.admin";

    private final PecheConfiguration pecheConfiguration;
    private final FishingZoneManager fishingZoneManager;
    private final AdminPecheGui gui;

    public AdminPecheCommand(PecheConfiguration pecheConfiguration, FishingZoneManager fishingZoneManager,
                             AdminPecheGui gui) {
        this.pecheConfiguration = Objects.requireNonNull(pecheConfiguration);
        this.fishingZoneManager = Objects.requireNonNull(fishingZoneManager);
        this.gui = Objects.requireNonNull(gui);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "Vous n'avez pas la permission.", "§c");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("config")) {
            if (!(sender instanceof Player player)) {
                send(sender, "Cette commande doit être exécutée en jeu.", "§c");
                return true;
            }
            gui.openConfig(player);
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("tools")) {
            if (!(sender instanceof Player player)) {
                send(sender, "Cette commande doit être exécutée en jeu.", "§c");
                return true;
            }
            handleTools(player, args);
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("books")
                || args[0].equalsIgnoreCase("livres"))) {
            if (!(sender instanceof Player player)) {
                send(sender, "Cette commande doit être exécutée en jeu.", "§c");
                return true;
            }
            giveItems(player, pecheConfiguration.createFishingEnchantmentBooks(),
                    "Aucun livre d'enchantement valide dans enchant.yml.",
                    "livre(s) d'enchantement généré(s) depuis enchant.yml.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("items")) {
            if (!(sender instanceof Player player)) {
                send(sender, "Cette commande doit être exécutée en jeu.", "§c");
                return true;
            }
            giveItems(player, pecheConfiguration.createFishingRods(),
                    "Aucune canne n'a pu être créée. Vérifie rod.yml.",
                    "canne(s) de pêche reçue(s).");
            return true;
        }
        send(sender, "Utilisation : /adminpeche <config|tools|books|items>", "§e");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return matching(List.of("config", "tools", "books", "livres", "items"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tools")) {
            return matching(List.of("pos1", "pos2", "start", "stop", "status"), args[1]);
        }
        return List.of();
    }

    private void handleTools(Player player, String[] args) {
        if (args.length != 2) {
            send(player, "Utilisation : /adminpeche tools <pos1|pos2|start|stop|status>", "§e");
            return;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "pos1", "pos2" -> {
                int corner = args[1].equalsIgnoreCase("pos1") ? 1 : 2;
                if (fishingZoneManager.setCorner(player, corner)) {
                    send(player, "Position " + corner + " enregistrée dans "
                            + player.getWorld().getName() + ".", "§a");
                }
            }
            case "start" -> {
                if (fishingZoneManager.start()) {
                    send(player, "Cercles de rareté activés.", "§a");
                } else {
                    send(player, "Définissez pos1 et pos2 dans le même monde avant de démarrer.", "§c");
                }
            }
            case "stop" -> {
                if (fishingZoneManager.stop()) {
                    send(player, "Cercles de rareté arrêtés.", "§a");
                } else {
                    send(player, "Les cercles de rareté étaient déjà arrêtés.", "§7");
                }
            }
            case "status" -> send(player, fishingZoneManager.isRunning()
                    ? "Cercles de rareté : actifs." : "Cercles de rareté : arrêtés.", "§7");
            default -> send(player, "Utilisation : /adminpeche tools <pos1|pos2|start|stop|status>", "§e");
        }
    }

    private void giveItems(Player player, List<ItemStack> items, String emptyMessage, String successSuffix) {
        if (items.isEmpty()) {
            send(player, emptyMessage, "§c");
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        send(player, items.size() + " " + successSuffix, "§a");
    }

    private List<String> matching(List<String> choices, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(java.util.Locale.ROOT);
        return choices.stream().filter(choice -> choice.startsWith(normalizedPrefix)).toList();
    }

    private void send(CommandSender sender, String message, String color) {
        sender.sendMessage(color + message);
    }
}
