package gg.fotia.tags.tag;

import gg.fotia.tags.FotiaTags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TagManager implements Listener {

    private final FotiaTags plugin;
    private final Map<String, Tag> tags = new HashMap<>();
    private final Map<UUID, PlayerTagData> playerDataCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingWriteCounts = new ConcurrentHashMap<>();
    private String defaultTag;
    private BukkitTask expireTask;
    private int expireTaskInterval = -1;

    public TagManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadTags() {
        tags.clear();

        ConfigurationSection tagsSection = plugin.getConfigManager().getTagsConfig().getConfigurationSection("tags");
        if (tagsSection != null) {
            for (String tagId : tagsSection.getKeys(false)) {
                ConfigurationSection tagSection = tagsSection.getConfigurationSection(tagId);
                if (tagSection == null) continue;

                Tag tag = new Tag(tagId);
                tag.setDisplayName(tagSection.getString("display-name", tagId));
                tag.setPrefix(tagSection.getString("prefix", ""));
                tag.setSuffix(tagSection.getString("suffix", ""));
                tag.setPrefix2(tagSection.getString("prefix2", ""));
                tag.setSuffix2(tagSection.getString("suffix2", ""));
                tag.setPermission(tagSection.getString("permission", ""));

                // GUI物品配置
                String materialStr = tagSection.getString("material", "PAPER");
                Material material = Material.matchMaterial(materialStr);
                tag.setMaterial(material != null ? material : Material.PAPER);
                tag.setItemModel(tagSection.getString("item-model", ""));
                tag.setTooltipStyle(tagSection.getString("tooltip-style", tagSection.getString("tooltip", "")));
                tag.setCustomModelData(tagSection.getInt("custom-model-data", 0));

                tags.put(tagId, tag);
            }
        }

        defaultTag = plugin.getConfigManager().getTagsConfig().getString("default-tag", "");

        plugin.getLogger().info("Loaded " + tags.size() + " tags");

        int interval = Math.max(1, plugin.getConfigManager().getConfig().getInt("settings.expire-check-interval", 60));
        if (expireTask == null || expireTask.isCancelled() || expireTaskInterval != interval) {
            startExpireCheckTask(interval);
        }
        refreshAllPlayerDisplays();
    }

    private void startExpireCheckTask(int interval) {
        if (expireTask != null) {
            expireTask.cancel();
        }
        expireTaskInterval = interval;
        expireTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshPlayerData(player.getUniqueId());
            }
        }, interval * 20L, interval * 20L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unloadPlayer(event.getPlayer());
    }

    public void loadPlayer(Player player) {
        refreshPlayerData(player.getUniqueId());
    }

    private void refreshPlayerData(UUID uuid) {
        plugin.getDatabaseManager().loadPlayerData(uuid)
                .thenAccept(data -> {
                    runSyncIfEnabled(() -> {
                        Player onlinePlayer = Bukkit.getPlayer(uuid);
                        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                            return;
                        }
                        if (pendingWriteCounts.containsKey(uuid)) {
                            return;
                        }
                        playerDataCache.put(uuid, data);
                        checkExpiredTags(uuid);
                        refreshPlayerDisplay(uuid);
                    });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to load player tag data: " + throwable.getMessage());
                    return null;
                });
    }

    public void unloadPlayer(Player player) {
        playerDataCache.remove(player.getUniqueId());
    }

    public PlayerTagData getPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    public void giveTag(UUID uuid, String tagId, long duration) {
        long expireTime;
        if (duration == -1) {
            expireTime = -1;
        } else {
            try {
                expireTime = Math.addExact(System.currentTimeMillis(), duration);
            } catch (ArithmeticException e) {
                plugin.getLogger().warning("Invalid tag duration overflow: " + uuid + " " + tagId);
                return;
            }
        }
        PlayerTagData data = playerDataCache.get(uuid);
        if (data != null) {
            data.addTag(tagId, expireTime);
            trackPlayerWrite(uuid, () -> plugin.getDatabaseManager().addPlayerTag(uuid, tagId, expireTime));
            refreshPlayerDisplay(uuid);
            return;
        }

        plugin.getDatabaseManager().addPlayerTag(uuid, tagId, expireTime).thenRun(() -> runSyncIfEnabled(() -> {
            PlayerTagData currentData = playerDataCache.get(uuid);
            if (currentData != null) {
                currentData.addTag(tagId, expireTime);
                refreshPlayerDisplay(uuid);
            }
        }));
    }

    public void removeTag(UUID uuid, String tagId) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data != null) {
            data.removeTag(tagId);
            trackPlayerWrite(uuid, () -> plugin.getDatabaseManager().removePlayerTag(uuid, tagId));
            refreshPlayerDisplay(uuid);
            return;
        }

        plugin.getDatabaseManager().removePlayerTag(uuid, tagId).thenRun(() -> runSyncIfEnabled(() -> {
            PlayerTagData currentData = playerDataCache.get(uuid);
            if (currentData != null) {
                currentData.removeTag(tagId);
                refreshPlayerDisplay(uuid);
            }
        }));
    }

    public void setCurrentTag(UUID uuid, String tagId) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data != null) {
            data.setCurrentTag(tagId);
            trackPlayerWrite(uuid, () -> plugin.getDatabaseManager().setSelectedTag(uuid, tagId));
            refreshPlayerDisplay(uuid);
            return;
        }

        plugin.getDatabaseManager().setSelectedTag(uuid, tagId).thenRun(() -> runSyncIfEnabled(() -> {
            PlayerTagData currentData = playerDataCache.get(uuid);
            if (currentData != null) {
                currentData.setCurrentTag(tagId);
                refreshPlayerDisplay(uuid);
            }
        }));
    }

    private void runSyncIfEnabled(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void trackPlayerWrite(UUID uuid, Supplier<CompletableFuture<Void>> writeSupplier) {
        pendingWriteCounts.merge(uuid, 1, Integer::sum);
        try {
            writeSupplier.get().whenComplete((ignored, throwable) -> completePlayerWrite(uuid));
        } catch (RuntimeException e) {
            completePlayerWrite(uuid);
            throw e;
        }
    }

    private void completePlayerWrite(UUID uuid) {
        pendingWriteCounts.compute(uuid, (key, count) -> {
            if (count == null || count <= 1) {
                return null;
            }
            return count - 1;
        });
    }

    public String getCurrentPrefix(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = getEffectiveTagId(data);
        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getPrefix() : "";
    }

    public String getCurrentSuffix(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = getEffectiveTagId(data);
        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getSuffix() : "";
    }

    public String getCurrentPrefix2(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = getEffectiveTagId(data);
        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getPrefix2() : "";
    }

    public String getCurrentSuffix2(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = getEffectiveTagId(data);
        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getSuffix2() : "";
    }

    public String getCurrentTagId(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        return getEffectiveTagId(data);
    }

    public String getCurrentTagName(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = getEffectiveTagId(data);
        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getDisplayName() : "";
    }

    private String getEffectiveTagId(PlayerTagData data) {
        String currentTag = data.getCurrentTag();
        if (currentTag != null && !currentTag.isEmpty()) {
            return currentTag;
        }
        if (defaultTag == null || defaultTag.isEmpty() || defaultTag.equalsIgnoreCase("disabled")) {
            return "";
        }
        return defaultTag;
    }

    public List<String> getOwnedTags(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return Collections.emptyList();
        return data.getValidTags();
    }

    public int getOwnedTagCount(UUID uuid) {
        return getOwnedTags(uuid).size();
    }

    public void checkExpiredTags(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return;

        List<String> expiredTags = data.checkAndRemoveExpiredTags();
        for (String tagId : expiredTags) {
            trackPlayerWrite(uuid, () -> plugin.getDatabaseManager().removePlayerTag(uuid, tagId));

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getMessageManager().send(player, "your-tag-expired", "tag", getTagDisplayName(tagId));
            }
        }

        if (data.getCurrentTag() == null && !expiredTags.isEmpty()) {
            trackPlayerWrite(uuid, () -> plugin.getDatabaseManager().setSelectedTag(uuid, null));
        }

        if (!expiredTags.isEmpty()) {
            refreshPlayerDisplay(uuid);
        }
    }

    public Tag getTag(String tagId) {
        return tags.get(tagId);
    }

    public Map<String, Tag> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    public String getTagDisplayName(String tagId) {
        Tag tag = tags.get(tagId);
        return tag != null ? tag.getDisplayName() : tagId;
    }

    public boolean tagExists(String tagId) {
        return tags.containsKey(tagId);
    }

    public String getDefaultTag() {
        return defaultTag;
    }

    public void shutdown() {
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }

        playerDataCache.clear();
        pendingWriteCounts.clear();
    }

    private void refreshPlayerDisplay(UUID uuid) {
        if (plugin.getPlayerDisplayManager() != null) {
            plugin.getPlayerDisplayManager().refreshPlayer(uuid);
        }
    }

    private void refreshAllPlayerDisplays() {
        if (plugin.getPlayerDisplayManager() != null) {
            plugin.getPlayerDisplayManager().refreshAll();
        }
    }
}
