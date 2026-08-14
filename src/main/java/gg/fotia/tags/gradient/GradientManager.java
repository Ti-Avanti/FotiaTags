package gg.fotia.tags.gradient;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.PlayerTagData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class GradientManager {

    private final FotiaTags plugin;
    private final GradientEffectRegistry registry;
    private BukkitTask animationTask;
    private boolean enabled;
    private int updateIntervalTicks;
    private final Set<UUID> activeAnimations = new HashSet<>();

    public GradientManager(FotiaTags plugin) {
        this.plugin = plugin;
        this.registry = new GradientEffectRegistry(plugin);
        reload();
    }

    public void reload() {
        registry.reload();
        enabled = plugin.getConfigManager().getConfig().getBoolean("dynamic-gradients.enabled", false);
        updateIntervalTicks = Math.max(1,
                plugin.getConfigManager().getConfig().getInt("dynamic-gradients.update-interval-ticks", 4));
        restartAnimationTask();
        if (plugin.getPlayerDisplayManager() != null) {
            plugin.getPlayerDisplayManager().refreshAll();
        }
    }

    public void shutdown() {
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public GradientEffect getEffect(String effectId) {
        return registry.get(effectId);
    }

    public List<GradientEffect> getEffects() {
        return List.copyOf(registry.all());
    }

    public String render(UUID uuid, String text, GradientTarget target) {
        if (!enabled || text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        if (data == null) {
            return text;
        }

        String selectedId = data.getGradientData().selectedEffect(System.currentTimeMillis());
        GradientEffect effect = registry.get(selectedId);
        if (effect == null || !effect.enabled() || !effect.appliesTo(target)) {
            return text;
        }
        return GradientFrameRenderer.render(text, effect, System.currentTimeMillis() / 50L);
    }

    public boolean owns(UUID uuid, String effectId) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        return data != null && data.getGradientData().owns(effectId, System.currentTimeMillis());
    }

    public String getSelectedEffect(UUID uuid) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        return data == null ? null : data.getGradientData().selectedEffect(System.currentTimeMillis());
    }

    public List<String> getOwnedEffects(UUID uuid) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        return data == null ? List.of() : data.getGradientData().validEffects(System.currentTimeMillis());
    }

    public long getExpireTime(UUID uuid, String effectId) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        return data == null ? 0L : data.getGradientData().expireTime(effectId);
    }

    public CompletableFuture<Void> grantEffect(UUID uuid, String effectId, long durationMillis) {
        GradientEffect effect = registry.get(effectId);
        if (effect == null || !effect.enabled()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown gradient effect: " + effectId));
        }

        long expireTime;
        try {
            expireTime = calculateExpireTime(durationMillis);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        return plugin.getDatabaseManager().grantGradientEffect(uuid, effect.id(), expireTime)
                .thenCompose(ignored -> runSync(() -> applyPersistedEffect(uuid, effect.id(), expireTime)));
    }

    public long calculateExpireTime(long durationMillis) {
        if (durationMillis == -1L) {
            return -1L;
        }
        try {
            return Math.addExact(System.currentTimeMillis(), durationMillis);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Gradient duration overflow", exception);
        }
    }

    public void applyPersistedEffect(UUID uuid, String effectId, long expireTime) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        if (data != null) {
            data.getGradientData().grant(effectId, expireTime);
        }
        try {
            refreshDisplay(uuid);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Gradient effect was saved but display refresh failed for " + uuid
                    + ": " + exception.getMessage());
        }
    }

    public CompletableFuture<Boolean> selectEffect(UUID uuid, String effectId) {
        GradientEffect effect = registry.get(effectId);
        PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
        if (!enabled || effect == null || !effect.enabled() || data == null
                || !data.getGradientData().owns(effect.id(), System.currentTimeMillis())) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getDatabaseManager().setSelectedGradientEffect(uuid, effect.id())
                .thenCompose(ignored -> runSync(() -> {
                    data.getGradientData().select(effect.id(), System.currentTimeMillis());
                    refreshDisplay(uuid);
                }))
                .thenApply(ignored -> true);
    }

    public CompletableFuture<Void> clearSelection(UUID uuid) {
        return plugin.getDatabaseManager().setSelectedGradientEffect(uuid, null)
                .thenCompose(ignored -> runSync(() -> {
                    PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
                    if (data != null) {
                        data.getGradientData().clearSelection();
                    }
                    refreshDisplay(uuid);
                }));
    }

    public CompletableFuture<Void> removeEffect(UUID uuid, String effectId) {
        return plugin.getDatabaseManager().removeGradientEffect(uuid, effectId)
                .thenCompose(ignored -> runSync(() -> {
                    PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
                    if (data != null) {
                        data.getGradientData().remove(effectId);
                    }
                    refreshDisplay(uuid);
                }));
    }

    private void restartAnimationTask() {
        shutdown();
        if (!enabled) {
            return;
        }
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshActivePlayers,
                updateIntervalTicks, updateIntervalTicks);
    }

    private void refreshActivePlayers() {
        long now = System.currentTimeMillis();
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            onlinePlayers.add(uuid);
            PlayerTagData data = plugin.getTagManager().getPlayerData(player.getUniqueId());
            if (data == null) {
                continue;
            }
            String configuredId = data.getGradientData().configuredSelection();
            String selectedId = data.getGradientData().selectedEffect(now);
            GradientEffect effect = registry.get(selectedId);
            if (effect != null && effect.enabled()) {
                activeAnimations.add(uuid);
                refreshDisplay(uuid);
            } else if (configuredId != null) {
                data.getGradientData().clearSelection();
                activeAnimations.remove(uuid);
                plugin.getDatabaseManager().setSelectedGradientEffect(uuid, null);
                refreshDisplay(uuid);
            }
        }
        activeAnimations.retainAll(onlinePlayers);
    }

    private void refreshDisplay(UUID uuid) {
        if (plugin.getPlayerDisplayManager() != null) {
            plugin.getPlayerDisplayManager().refreshPlayer(uuid);
        }
    }

    private CompletableFuture<Void> runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                task.run();
                future.complete(null);
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }
}
