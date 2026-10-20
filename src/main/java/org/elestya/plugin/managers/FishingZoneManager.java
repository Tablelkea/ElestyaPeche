package org.elestya.plugin.managers;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.elestya.plugin.sources.FishRarity;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingZoneManager {

    private static final String ROOT = "fishing-tools.zone";
    private static final int CIRCLE_LIFETIME_TICKS = 600;
    private static final double CIRCLE_RADIUS = 2.2;
    private static final double CIRCLE_RADIUS_SQUARED = CIRCLE_RADIUS * CIRCLE_RADIUS;
    private static final int CIRCLE_POINTS = 32;

    private final JavaPlugin plugin;
    private final File settingsFile;
    private final Map<FishRarity, ActiveCircle> circles = new EnumMap<>(FishRarity.class);
    private BukkitTask task;
    private Zone zone;
    private long currentTick;
    private boolean warnedNoLocation;

    public FishingZoneManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        settingsFile = new File(plugin.getDataFolder(), "settings.yml");
    }

    public boolean setCorner(Player player, int corner) {
        YamlConfiguration yaml = loadSettings();
        String path = ROOT + ".pos" + corner;
        yaml.set(path + ".world", player.getWorld().getName());
        yaml.set(path + ".x", player.getLocation().getBlockX());
        yaml.set(path + ".z", player.getLocation().getBlockZ());
        try {
            yaml.save(settingsFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Impossible de sauvegarder la zone de pêche : " + exception.getMessage());
            player.sendMessage("§cImpossible de sauvegarder la position de la zone.");
            return false;
        }
        return true;
    }

    public boolean start() {
        Zone configuredZone = readZone();
        if (configuredZone == null) {
            return false;
        }
        stopTask();
        zone = configuredZone;
        circles.clear();
        try {
            YamlConfiguration yaml = loadSettings();
            yaml.set("fishing-tools.enabled", true);
            yaml.save(settingsFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Impossible de sauvegarder l'état de la zone : " + exception.getMessage());
            zone = null;
            return false;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 5L);
        return true;
    }

    public boolean stop() {
        boolean wasRunning = task != null || loadSettings().getBoolean("fishing-tools.enabled", false);
        stopTask();
        circles.clear();
        zone = null;
        try {
            YamlConfiguration yaml = loadSettings();
            yaml.set("fishing-tools.enabled", false);
            yaml.save(settingsFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Impossible de sauvegarder l'arrêt de la zone : " + exception.getMessage());
            return false;
        }
        return wasRunning;
    }

    public void reloadFromConfig() {
        if (!loadSettings().getBoolean("fishing-tools.enabled", false)) {
            return;
        }
        if (!start()) {
            plugin.getLogger().warning("Zone de pêche activée dans settings.yml, mais ses positions sont invalides.");
        }
    }

    public void shutdown() {
        stopTask();
        circles.clear();
        zone = null;
    }

    public FishRarity getRarityAt(Location location) {
        if (location == null || zone == null || !zone.world.equals(location.getWorld())) {
            return null;
        }
        FishRarity closest = null;
        double closestDistance = CIRCLE_RADIUS_SQUARED;
        for (Map.Entry<FishRarity, ActiveCircle> entry : circles.entrySet()) {
            Location center = entry.getValue().center;
            double dx = center.getX() - location.getX();
            double dz = center.getZ() - location.getZ();
            double distance = dx * dx + dz * dz;
            if (Math.abs(center.getY() - location.getY()) <= 3.0 && distance <= closestDistance) {
                closest = entry.getKey();
                closestDistance = distance;
            }
        }
        return closest;
    }

    public boolean isRunning() {
        return task != null;
    }

    private void tick() {
        if (zone == null) {
            return;
        }
        currentTick += 5;
        for (FishRarity rarity : FishRarity.values()) {
            ActiveCircle circle = circles.get(rarity);
            if (circle == null || currentTick >= circle.expiresAt) {
                circles.remove(rarity);
                Location replacement = findLocation(circle == null ? null : circle.center);
                if (replacement != null) {
                    circles.put(rarity, new ActiveCircle(replacement, currentTick + CIRCLE_LIFETIME_TICKS));
                    warnedNoLocation = false;
                } else if (!warnedNoLocation) {
                    plugin.getLogger().warning("Aucun emplacement chargé et libre dans la zone de pêche "
                            + "pour faire apparaître un cercle.");
                    warnedNoLocation = true;
                }
                continue;
            }
            drawCircle(circle.center, colorFor(rarity));
        }
    }

    private Location findLocation(Location avoid) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = random.nextInt(zone.minX, zone.maxX + 1);
            int z = random.nextInt(zone.minZ, zone.maxZ + 1);
            if (!zone.world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = zone.world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(zone.world, x + 0.5, y + 0.1, z + 0.5);
            boolean overlaps = (avoid != null && avoid.distanceSquared(candidate) < 16.0)
                    || circles.values().stream()
                    .anyMatch(circle -> circle.center.distanceSquared(candidate) < 16.0);
            if (!overlaps) {
                return candidate;
            }
        }
        return null;
    }

    private void drawCircle(Location center, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        for (int point = 0; point < CIRCLE_POINTS; point++) {
            double angle = 2 * Math.PI * point / CIRCLE_POINTS;
            Location particle = center.clone().add(Math.cos(angle) * CIRCLE_RADIUS, 0, Math.sin(angle) * CIRCLE_RADIUS);
            center.getWorld().spawnParticle(Particle.DUST, particle, 1, dust);
        }
    }

    private Color colorFor(FishRarity rarity) {
        return switch (rarity) {
            case BASIQUE -> Color.fromRGB(145, 145, 145);
            case BRILLANT -> Color.fromRGB(40, 190, 220);
            case EPIQUE -> Color.fromRGB(160, 65, 220);
            case LEGENDAIRE -> Color.fromRGB(235, 130, 35);
            case MYSTIQUE -> Color.fromRGB(255, 205, 35);
        };
    }

    private Zone readZone() {
        YamlConfiguration yaml = loadSettings();
        String world1 = yaml.getString(ROOT + ".pos1.world");
        String world2 = yaml.getString(ROOT + ".pos2.world");
        if (world1 == null || !world1.equals(world2)) {
            return null;
        }
        World world = plugin.getServer().getWorld(world1);
        if (world == null) {
            return null;
        }
        String first = ROOT + ".pos1.";
        String second = ROOT + ".pos2.";
        if (!yaml.contains(first + "x") || !yaml.contains(first + "z")
                || !yaml.contains(second + "x") || !yaml.contains(second + "z")) {
            return null;
        }
        int x1 = yaml.getInt(first + "x");
        int z1 = yaml.getInt(first + "z");
        int x2 = yaml.getInt(second + "x");
        int z2 = yaml.getInt(second + "z");
        return new Zone(world, Math.min(x1, x2), Math.max(x1, x2),
                Math.min(z1, z2), Math.max(z1, z2));
    }

    private YamlConfiguration loadSettings() {
        return YamlConfiguration.loadConfiguration(settingsFile);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private record Zone(World world, int minX, int maxX, int minZ, int maxZ) {
    }

    private record ActiveCircle(Location center, long expiresAt) {
    }
}
