package gg.fotia.tags.particle;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.tag.TagManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ParticleManager {

    private static final List<String> DEFAULT_EFFECT_RESOURCES = List.of(
            "particles/halo.yml",
            "particles/trail.yml",
            "particles/feet.yml",
            "particles/burst.yml"
    );

    private final FotiaTags plugin;
    private final Map<String, ParticleEffect> effects = new HashMap<>();
    private final Map<UUID, Long> lastSpawnTicks = new HashMap<>();
    private BukkitTask task;
    private boolean enabled;
    private boolean onlyVisibleToNearby;
    private int maxAmount;
    private double viewDistanceSquared;
    private long tick;

    public ParticleManager(FotiaTags plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        loadSettings();
        loadEffects();
        restartTask();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        effects.clear();
        lastSpawnTicks.clear();
    }

    public void refreshPlayer(UUID uuid) {
        lastSpawnTicks.remove(uuid);
    }

    public void refreshAll() {
        lastSpawnTicks.clear();
    }

    public ParticleEffect getEffect(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return effects.get(normalizeId(id));
    }

    public List<ParticleEffect> getEffects() {
        return effects.values().stream()
                .sorted(Comparator.comparing(ParticleEffect::id))
                .toList();
    }

    public Collection<String> getEffectIds() {
        return getEffects().stream()
                .map(ParticleEffect::id)
                .toList();
    }

    private void loadSettings() {
        enabled = plugin.getConfigManager().getConfig().getBoolean("particles.enabled", false);
        onlyVisibleToNearby = plugin.getConfigManager().getConfig().getBoolean("particles.only-visible-to-nearby", true);
        maxAmount = Math.max(1, plugin.getConfigManager().getConfig().getInt("particles.max-per-player-amount", 12));
        double viewDistance = Math.max(1.0, plugin.getConfigManager().getConfig().getDouble("particles.view-distance", 24.0));
        viewDistanceSquared = viewDistance * viewDistance;
    }

    private void loadEffects() {
        effects.clear();

        File folder = new File(plugin.getDataFolder(), "particles");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create particles folder.");
            return;
        }

        for (String resource : DEFAULT_EFFECT_RESOURCES) {
            File file = new File(plugin.getDataFolder(), resource);
            if (!file.exists()) {
                plugin.saveResource(resource, false);
            }
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            ParticleEffect effect = loadEffect(file);
            if (effect != null) {
                effects.put(normalizeId(effect.id()), effect);
            }
        }
    }

    private ParticleEffect loadEffect(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = file.getName().substring(0, file.getName().length() - 4);
        Particle particle = parseParticle(config.getString("particle", config.getString("type", "END_ROD")));
        if (particle == null) {
            plugin.getLogger().warning("Skipping particle effect " + id + ": invalid particle type.");
            return null;
        }

        Material material = Material.matchMaterial(config.getString("gui.material", config.getString("material", "BLAZE_POWDER")));
        if (material == null) {
            material = Material.BLAZE_POWDER;
        }

        return new ParticleEffect(
                id,
                config.getString("display-name", id),
                config.getBoolean("enabled", true),
                particle,
                config.getString("style", id).toLowerCase(Locale.ROOT),
                Math.max(1, config.getInt("interval-ticks", 10)),
                Math.max(1, Math.min(config.getInt("amount", 4), maxAmount)),
                Math.max(0.1, config.getDouble("radius", 0.8)),
                config.getDouble("y-offset", 1.0),
                Math.max(0.0, config.getDouble("speed", 0.01)),
                material,
                config.getString("gui.item-model", config.getString("item-model", "")),
                config.getString("gui.tooltip-style", config.getString("tooltip-style", "")),
                Math.max(0, config.getInt("gui.custom-model-data", config.getInt("custom-model-data", 0))),
                config.getBoolean("gui.glow", config.getBoolean("glow", false))
        );
    }

    private Particle parseParticle(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.toUpperCase(Locale.ROOT)
                .replace("MINECRAFT:", "")
                .replace('-', '_');
        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void restartTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastSpawnTicks.clear();

        if (!enabled || effects.isEmpty()) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ParticleEffect effect = getCurrentEffect(player);
            if (effect == null || !effect.enabled()) {
                continue;
            }

            long lastTick = lastSpawnTicks.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
            if (lastTick != Long.MIN_VALUE && tick - lastTick < effect.intervalTicks()) {
                continue;
            }

            lastSpawnTicks.put(player.getUniqueId(), tick);
            spawn(player, effect);
        }
    }

    private ParticleEffect getCurrentEffect(Player player) {
        if (plugin.getTagManager() == null) {
            return null;
        }

        String tagId = plugin.getTagManager().getCurrentTagId(player.getUniqueId());
        if (tagId == null || tagId.isBlank() || TagManager.CUSTOM_TAG_ID.equals(tagId)) {
            return null;
        }

        if (plugin.getTagManager().isCustomTagId(tagId)) {
            CustomTag customTag = plugin.getTagManager().getCustomTag(player.getUniqueId(), tagId);
            if (customTag == null || customTag.getParticleEffect().isBlank()) {
                return null;
            }
            return getEffect(customTag.getParticleEffect());
        }

        Tag tag = plugin.getTagManager().getTag(tagId);
        if (tag == null || tag.getParticleEffect().isBlank()) {
            return null;
        }

        return getEffect(tag.getParticleEffect());
    }

    private void spawn(Player player, ParticleEffect effect) {
        Location base = player.getLocation();
        switch (effect.style()) {
            case "halo" -> spawnHalo(player, effect, base);
            case "trail" -> spawnTrail(player, effect, base);
            case "feet" -> spawnFeet(player, effect, base);
            case "burst" -> spawnBurst(player, effect, base);
            default -> spawnBurst(player, effect, base);
        }
    }

    private void spawnHalo(Player player, ParticleEffect effect, Location base) {
        double rotation = tick * 0.15;
        int points = Math.max(4, effect.amount());
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i / points);
            Location location = base.clone().add(
                    Math.cos(angle) * effect.radius(),
                    effect.yOffset(),
                    Math.sin(angle) * effect.radius()
            );
            spawnParticle(player, effect, location, 1, 0.0, 0.0, 0.0);
        }
    }

    private void spawnTrail(Player player, ParticleEffect effect, Location base) {
        Vector direction = base.getDirection();
        direction.setY(0);
        if (direction.lengthSquared() < 0.0001) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize().multiply(-effect.radius());

        for (int i = 0; i < effect.amount(); i++) {
            double distance = (i + 1.0) / effect.amount();
            Location location = base.clone()
                    .add(direction.clone().multiply(distance))
                    .add(0, effect.yOffset(), 0);
            spawnParticle(player, effect, location, 1, 0.05, 0.05, 0.05);
        }
    }

    private void spawnFeet(Player player, ParticleEffect effect, Location base) {
        double rotation = tick * 0.2;
        int points = Math.max(4, effect.amount());
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i / points);
            Location location = base.clone().add(
                    Math.cos(angle) * effect.radius(),
                    effect.yOffset(),
                    Math.sin(angle) * effect.radius()
            );
            spawnParticle(player, effect, location, 1, 0.0, 0.0, 0.0);
        }
    }

    private void spawnBurst(Player player, ParticleEffect effect, Location base) {
        Location location = base.clone().add(0, effect.yOffset(), 0);
        spawnParticle(player, effect, location, effect.amount(), effect.radius(), effect.radius(), effect.radius());
    }

    private void spawnParticle(Player owner, ParticleEffect effect, Location location, int amount, double offsetX, double offsetY, double offsetZ) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        if (!onlyVisibleToNearby) {
            world.spawnParticle(effect.particle(), location, amount, offsetX, offsetY, offsetZ, effect.speed());
            return;
        }

        for (Player viewer : new ArrayList<>(world.getPlayers())) {
            if (viewer.getLocation().distanceSquared(owner.getLocation()) <= viewDistanceSquared) {
                viewer.spawnParticle(effect.particle(), location, amount, offsetX, offsetY, offsetZ, effect.speed());
            }
        }
    }

    private String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
