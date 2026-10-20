package org.elestya.plugin.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.elestya.plugin.ressources.FishRessourceLoader;
import org.elestya.plugin.ressources.PecheConfiguration;
import org.elestya.plugin.sources.Fish;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FishingManager {

    private final JavaPlugin plugin;
    private final NamespacedKey fishIdKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey rodTierKey;
    private final NamespacedKey visualBobberKey;
    private final FishRessourceLoader fishLoader;
    private final PecheConfiguration pecheConfiguration;
    private final FishingZoneManager fishingZoneManager;


    private final Map<UUID, List<VisualBobber>> visualHooks = new HashMap<>();
    private final Map<UUID, BukkitTask> visualHookTasks = new HashMap<>();
    public final Map<UUID, FishHook> mainHooks = new HashMap<>();
    /** UUID du hook -> tick de la dernière touche. */
    public final Map<UUID, Integer> biteTicks = new HashMap<>();

    public FishingManager(JavaPlugin plugin, PecheConfiguration pecheConfiguration, FishRessourceLoader fishLoader,
                          FishingZoneManager fishingZoneManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fishLoader = Objects.requireNonNull(fishLoader);
        this.fishingZoneManager = Objects.requireNonNull(fishingZoneManager);
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.rarityKey = new NamespacedKey(plugin, "fish_rarity");
        this.weightKey = new NamespacedKey(plugin, "fish_weight");
        this.rodTierKey = new NamespacedKey(plugin, "rod_tier");
        this.visualBobberKey = new NamespacedKey(plugin, "multi_hamecon_visual");
        this.pecheConfiguration = Objects.requireNonNull(pecheConfiguration);
    }

    public void clearAllVisualHooks() {
        for (UUID playerId : new ArrayList<>(visualHooks.keySet())) {
            removeVisualHooks(playerId);
        }
        for (var world : plugin.getServer().getWorlds()) {
            for (FishHook hook : world.getEntitiesByClass(FishHook.class)) {
                if (isVisualHook(hook)) {
                    hook.remove();
                }
            }
        }
        mainHooks.clear();
        biteTicks.clear();
    }

    public boolean isVisualHook(FishHook hook) {
        return hook != null
                && hook.getPersistentDataContainer().has(visualBobberKey, PersistentDataType.BYTE);
    }

    /**
     * Donne un poisson pour chaque hook (autre que celui déjà géré par Minecraft)
     * qui a reçu une touche encore valide.
     */
    public void rewardOtherHooks(Player player, FishHook retrievedHook, ItemStack rod, int rodTier,
                                  ItemStack fallback) {
        UUID playerId = player.getUniqueId();
        if (!mainHooks.containsKey(playerId)) {
            return;
        }
        List<FishHook> hooks = new ArrayList<>();
        hooks.add(mainHooks.get(playerId));
        List<VisualBobber> extras = visualHooks.get(playerId);
        if (extras != null) {
            for (VisualBobber extra : extras) {
                hooks.add(extra.hook());
            }
        }

        int now = plugin.getServer().getCurrentTick();
        for (FishHook hook : hooks) {
            if (hook == null || !hook.isValid()
                    || hook.getUniqueId().equals(retrievedHook.getUniqueId())) {
                continue;
            }
            Integer biteTick = biteTicks.remove(hook.getUniqueId());
            if (debugMode()) {
                plugin.getLogger().info("[peche] hook " + hook.getUniqueId() + " visuel=" + isVisualHook(hook)
                        + " touche=" + (biteTick == null ? "aucune" : (now - biteTick) + " ticks"));
            }
            if (biteTick == null || now - biteTick > biteWindowTicks()) {
                continue;
            }
            dropCatch(player, hook, rod, rodTier, fallback);
        }
    }

    public void dropCatch(Player player, FishHook hook, ItemStack rod, int rodTier, ItemStack fallback) {
        ItemStack result = createCustomFish(rod, rodTier, hook.getLocation());
        if (result == null) {
            result = fallback.clone();
        }
        Location hookLocation = hook.getLocation();
        Vector toPlayer = player.getLocation().toVector().subtract(hookLocation.toVector());
        double distance = toPlayer.length();
        Vector velocity = toPlayer.multiply(0.1);
        velocity.setY(velocity.getY() + Math.sqrt(distance) * 0.08);
        hook.getWorld().dropItem(hookLocation, result, item -> item.setVelocity(velocity));
        player.giveExp(ThreadLocalRandom.current().nextInt(1, 7));
        sendCatchMessage(player, result);
        consumeRodUse(player, rod, rodTier);
    }

    public void consumeRodUse(Player player, ItemStack rod, int rodTier) {
        int remaining = pecheConfiguration.consumeRodUse(rod, rodTier);
        if (remaining == -2) {
            return;
        }
        if (remaining >= 0 && managerRodIsInHand(player, rodTier)) {
            if (remaining == 0) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                player.sendMessage(Component.text("Votre canne est épuisée."));
            } else {
                player.getInventory().setItemInMainHand(rod);
            }
        }
    }

    private boolean managerRodIsInHand(Player player, int rodTier) {
        return rodTier > 0 && getRodTier(player) == rodTier;
    }

    public void launchVisualHooks(Player player, FishHook mainHook, int hooksPerCatch) {
        UUID playerId = player.getUniqueId();
        mainHooks.put(playerId, mainHook);
        // On attend 1 tick : le hook principal doit être entièrement ajouté au monde
        // avant de créer des hooks supplémentaires pour le même joueur.
        visualHookTasks.put(playerId, plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !mainHook.isValid()) {
                removeVisualHooks(playerId);
                return;
            }

            List<VisualBobber> extras = new ArrayList<>(hooksPerCatch - 1);
            Vector direction = mainHook.getVelocity().clone().setY(0);
            if (direction.lengthSquared() < 1.0e-6) {
                direction = player.getLocation().getDirection().setY(0);
            }
            if (direction.lengthSquared() < 1.0e-6) {
                direction = new Vector(0, 0, 1);
            }
            Vector forward = direction.clone().normalize();
            Vector side = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
            for (int i = 1; i < hooksPerCatch; i++) {
                int directionSign = i % 2 == 1 ? 1 : -1;
                int spread = (i + 1) / 2;
                Vector offset = side.clone()
                        .multiply(directionSign * (pecheConfiguration.getHookSpacing()
                                + (spread - 1) * pecheConfiguration.getHookSpacingStep()))
                        .add(forward.clone().multiply(((i % 3) - 1) * pecheConfiguration.getHookStagger()));
                extras.add(new VisualBobber(createVisualHook(player, mainHook, offset), offset));
            }
            visualHooks.put(playerId, extras);
            visualHookTasks.put(playerId, plugin.getServer().getScheduler().runTaskTimer(
                    plugin, () -> updateVisualHooks(player, mainHook), 1L, 1L));
        }));
    }

    public void removeVisualHooks(UUID playerId) {
        BukkitTask task = visualHookTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        List<VisualBobber> hooks = visualHooks.remove(playerId);
        if (hooks != null) {
            for (VisualBobber bobber : hooks) {
                biteTicks.remove(bobber.hook().getUniqueId());
                bobber.remove();
            }
        }
        FishHook main = mainHooks.remove(playerId);
        if (main != null) {
            biteTicks.remove(main.getUniqueId());
            // Au tick suivant, pour ne pas gêner le rembobinage vanilla en cours.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (main.isValid()) {
                    main.remove();
                }
            });
        }
    }

    public FishHook createVisualHook(Player player, FishHook mainHook, Vector offset) {
        FishHook hook = player.launchProjectile(FishHook.class, new Vector());
        hook.setPersistent(false);
        hook.setGravity(false);
        hook.getPersistentDataContainer().set(visualBobberKey, PersistentDataType.BYTE, (byte) 1);
        hook.setVelocity(new Vector());
        hook.teleport(mainHook.getLocation().add(offset));
        return hook;
    }

    public void updateVisualHooks(Player player, FishHook mainHook) {
        UUID playerId = player.getUniqueId();
        List<VisualBobber> bobbers = visualHooks.get(playerId);
        if (bobbers == null || !player.isOnline() || !mainHook.isValid()) {
            removeVisualHooks(playerId);
            return;
        }
        for (VisualBobber bobber : bobbers) {
            FishHook hook = bobber.hook();
            if (!hook.isValid()) {
                removeVisualHooks(playerId);
                return;
            }
            hook.setVelocity(new Vector());
            hook.teleport(mainHook.getLocation().add(bobber.offset()));
        }
    }

    public record VisualBobber(FishHook hook, Vector offset) {
        private void remove() {
            if (hook.isValid()) {
                hook.remove();
            }
        }
    }

    public ItemStack createCustomFish(ItemStack rod, int rodTier) {
        return createCustomFish(rod, rodTier, null);
    }

    public ItemStack createCustomFish(ItemStack rod, int rodTier, Location catchLocation) {
        var fishes = fishLoader.getAll();
        double customChance = pecheConfiguration.getCustomFishChance(rodTier, rod);
        if (fishes.isEmpty() || customChance <= 0
                || ThreadLocalRandom.current().nextDouble(100) >= customChance) {
            return null;
        }

        Fish fish = pecheConfiguration.selectFish(fishes,
                fishingZoneManager.getRarityAt(catchLocation)).orElse(null);
        double weightMultiplier = pecheConfiguration.getWeightMultiplier(rodTier);
        if (fish == null || weightMultiplier <= 0) {
            return null;
        }
        var customFishMaterial = pecheConfiguration.getCustomFishMaterial();
        if (customFishMaterial.isEmpty()) {
            return null;
        }

        double weightRoll = ThreadLocalRandom.current().nextDouble();
        double weightBias = pecheConfiguration.getWeightBias(rod);
        double biasedRoll = 1 - Math.pow(1 - weightRoll, 1 + weightBias * 4);
        double baseWeight = fish.wmin() + (fish.wmax() - fish.wmin()) * biasedRoll;
        double weight = Math.clamp(baseWeight * weightMultiplier, fish.wmin(), fish.wmax());
        weight = Math.clamp(Math.round(weight * 1000) / 1000.0, fish.wmin(), fish.wmax());
        ItemStack result = new ItemStack(customFishMaterial.get());
        var meta = result.getItemMeta();
        meta.displayName(pecheConfiguration.getFishDisplayName(fish, weight));
        meta.lore(pecheConfiguration.getFishLore(fish, weight));
        meta.setItemModel(NamespacedKey.fromString(fish.model()));
        meta.getPersistentDataContainer().set(fishIdKey, PersistentDataType.STRING, fish.id());
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, fish.rarity().name());
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
        result.setItemMeta(meta);
        return result;
    }

    public int getRodTier(Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD || !rod.hasItemMeta()) {
            return 0;
        }

        Integer tier = rod.getItemMeta().getPersistentDataContainer()
                .get(rodTierKey, PersistentDataType.INTEGER);
        if (tier == null || tier < 1) {
            return 0;
        }
        return tier;
    }

    public void sendCatchMessage(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        String fishId = pdc.get(fishIdKey, PersistentDataType.STRING);
        String rarityName = pdc.get(rarityKey, PersistentDataType.STRING);
        Double weight = pdc.get(weightKey, PersistentDataType.DOUBLE);
        if (fishId == null || rarityName == null || weight == null) {
            return; // pas un poisson custom
        }

        Component name = meta.hasDisplayName() ? meta.displayName() : Component.text(fishId);
        String rarity = rarityName.substring(0, 1).toUpperCase(Locale.ROOT)
                + rarityName.substring(1).toLowerCase(Locale.ROOT);
        double price = pecheConfiguration.getSellPrice(rarityName, weight);

        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        String weightText = new DecimalFormat("#,##0", symbols).format(Math.round(weight * 1000));
        String priceText = new DecimalFormat("#,##0.00", symbols).format(price);

        player.sendMessage(Component.text("🎣 Vous avez pêché ", NamedTextColor.GRAY)
                .append(name)
                .append(Component.text(" | Rareté : ", NamedTextColor.DARK_GRAY))
                .append(Component.text(rarity, NamedTextColor.GOLD))
                .append(Component.text(" | Poids : ", NamedTextColor.DARK_GRAY))
                .append(Component.text(weightText + " g", NamedTextColor.AQUA))
                .append(Component.text(" | Valeur : ", NamedTextColor.DARK_GRAY))
                .append(Component.text(priceText + " " + pecheConfiguration.getCurrencySymbol(),
                        NamedTextColor.GREEN)));
    }

    public boolean debugMode(){
        return pecheConfiguration.isFishingDebugEnabled();
    }

    public int biteWindowTicks() {
        return pecheConfiguration.getBiteWindowTicks();
    }

    public PecheConfiguration pecheConfiguration(){
        return this.pecheConfiguration;
    }

    public double getSellPrice(ItemStack item){

        if (item == null || !item.hasItemMeta()) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        var pdc = meta.getPersistentDataContainer();

        String rarityName = pdc.get(rarityKey, PersistentDataType.STRING);
        Double weight = pdc.get(weightKey, PersistentDataType.DOUBLE);
        if (rarityName == null || weight == null) {
            return 0; // pas un poisson custom donc 0
        }

        return pecheConfiguration.getSellPrice(rarityName, weight);
    }

}
