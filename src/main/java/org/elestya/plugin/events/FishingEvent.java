package org.elestya.plugin.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.elestya.plugin.managers.FishingManager;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;

public class FishingEvent implements Listener {

    private final JavaPlugin plugin;
    private final FishingManager manager;

    private static final double ALERT_HEIGHT = 2.2;

    public FishingEvent(JavaPlugin plugin, FishingManager manager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.manager = Objects.requireNonNull(manager);
    }

    @EventHandler
    public void onFishing(@NonNull PlayerFishEvent e) {
        Player player = e.getPlayer();
        UUID playerId = player.getUniqueId();
        FishHook eventHook = e.getHook();
        PlayerFishEvent.State state = e.getState();
        boolean visual = manager.isVisualHook(eventHook);

        if (state == PlayerFishEvent.State.FISHING) {
            if (visual) {
                return;
            }
            ItemStack rod = player.getInventory().getItemInMainHand();
            manager.removeVisualHooks(playerId);
            int hooksPerCatch = manager.pecheConfiguration().getHooksPerCatch(rod);
            if (hooksPerCatch > 1 && !e.isCancelled()) {
                manager.launchVisualHooks(player, eventHook, hooksPerCatch);
            }
            return;
        }

        if (manager.debugMode()) {
            plugin.getLogger().info("[peche] " + player.getName() + " state=" + state
                    + " hookVisuel=" + visual
                    + " sessionMulti=" + manager.mainHooks.containsKey(playerId)
                    + " caught=" + e.getCaught());
        }

        if (state == PlayerFishEvent.State.BITE && manager.mainHooks.containsKey(playerId)) {
            manager.biteTicks.put(eventHook.getUniqueId(), plugin.getServer().getCurrentTick());
            player.playSound(eventHook.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
            playBiteEffect(player, eventHook);
            return;
        }

        // Une touche sur n'importe quel hook de la session (principal ou rajouté) est mémorisée.
        if (state == PlayerFishEvent.State.BITE) {
            if (manager.mainHooks.containsKey(playerId)) {
                manager.biteTicks.put(eventHook.getUniqueId(), plugin.getServer().getCurrentTick());
            }
            return;
        }

        // Un hook rajouté qui touche le sol ne doit pas arrêter la session.
        if (visual && state == PlayerFishEvent.State.IN_GROUND) {
            return;
        }

        ItemStack rod = player.getInventory().getItemInMainHand();
        int rodTier = manager.getRodTier(player);
        ItemStack fallback = new ItemStack(Material.COD);

        if (state == PlayerFishEvent.State.CAUGHT_FISH && e.getCaught() instanceof Item caught) {
            ItemStack vanillaCatch = caught.getItemStack().clone();
            fallback = vanillaCatch;
            ItemStack result = manager.createCustomFish(rod, rodTier);
            caught.setItemStack(result != null ? result : vanillaCatch);
        }

        if (state == PlayerFishEvent.State.REEL_IN || state == PlayerFishEvent.State.CAUGHT_FISH) {
            manager.rewardOtherHooks(player, eventHook, rod, rodTier, fallback);
        }

        if (state == PlayerFishEvent.State.REEL_IN
                || state == PlayerFishEvent.State.CAUGHT_FISH
                || state == PlayerFishEvent.State.CAUGHT_ENTITY
                || state == PlayerFishEvent.State.IN_GROUND) {
            manager.removeVisualHooks(playerId);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        manager.removeVisualHooks(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        manager.removeVisualHooks(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent e) {
        manager.removeVisualHooks(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent e) {
        manager.removeVisualHooks(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
        manager.removeVisualHooks(e.getPlayer().getUniqueId());
    }

    private void playBiteEffect(Player player, FishHook hook) {
        Location loc = hook.getLocation();
        World world = loc.getWorld();

        // 1. Le "!" géant au-dessus du bouchon (suit le bouchon, disparaît après la fenêtre de remontée)
        TextDisplay alert = world.spawn(loc.clone().add(0, ALERT_HEIGHT, 0), TextDisplay.class, d -> {
            d.text(Component.text(" ! ", NamedTextColor.WHITE, TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setBackgroundColor(Color.fromARGB(255, 220, 20, 20));   // rouge opaque (alpha 255)
            d.setTextOpacity((byte) -1);                              // texte 100 % opaque
            d.setShadowed(false);
            d.setBrightness(new Display.Brightness(15, 15));          // pas assombri la nuit ni dans l'ombre
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(4f), new Quaternionf()));
        });

        // 2. Geyser d'eau
        world.spawnParticle(Particle.SPLASH, loc.clone().add(0, 0.2, 0), 60, 0.15, 0.8, 0.15, 0.15);
        world.spawnParticle(Particle.BUBBLE_POP, loc.clone().add(0, 0.2, 0), 25, 0.2, 0.3, 0.2, 0.05);

        // 3. Cercle qui s'étend + "!" qui suit le bouchon
        new BukkitRunnable() {
            double radius = 0.3;
            int tick = 0;

            @Override
            public void run() {
                if (!hook.isValid() || tick >= FishingManager.BITE_WINDOW_TICKS / 2) {
                    alert.remove();
                    cancel();
                    return;
                }
                Location center = hook.getLocation();
                alert.teleport(center.clone().add(0, ALERT_HEIGHT, 0));
                if (tick < 8) {
                    int points = 24;
                    for (int i = 0; i < points; i++) {
                        double angle = 2 * Math.PI * i / points;
                        world.spawnParticle(Particle.SPLASH,
                                center.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius),
                                2, 0.02, 0.02, 0.02, 0.02);
                    }
                    radius += 0.2;
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // 4. Son + message
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1.3f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        player.sendActionBar(Component.text("🐟 Ça mord ! Remonte !", NamedTextColor.GOLD, TextDecoration.BOLD));
    }
}