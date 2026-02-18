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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagManager implements Listener {

    private final FotiaTags plugin;
    private final Map<String, Tag> tags = new HashMap<>();
    private final Map<UUID, PlayerTagData> playerDataCache = new ConcurrentHashMap<>();
    private String defaultTag;

    public TagManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadTags() {
        tags.clear();

        ConfigurationSection tagsSection = plugin.getConfigManager().getTagsConfig().getConfigurationSection("tags");
        if (tagsSection == null) {
            return;
        }

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
            tag.setCustomModelData(tagSection.getInt("custom-model-data", 0));

            tags.put(tagId, tag);
        }

        defaultTag = plugin.getConfigManager().getTagsConfig().getString("default-tag", "");

        plugin.getLogger().info("Loaded " + tags.size() + " tags");

        startExpireCheckTask();
    }

    private void startExpireCheckTask() {
        int interval = plugin.getConfigManager().getConfig().getInt("settings.expire-check-interval", 60);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkExpiredTags(player.getUniqueId());
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
        plugin.getDatabaseManager().loadPlayerData(player.getUniqueId())
                .thenAccept(data -> {
                    playerDataCache.put(player.getUniqueId(), data);
                    checkExpiredTags(player.getUniqueId());
                });
    }

    public void unloadPlayer(Player player) {
        PlayerTagData data = playerDataCache.remove(player.getUniqueId());
        if (data != null) {
            plugin.getDatabaseManager().savePlayerData(data);
        }
    }

    public PlayerTagData getPlayerData(UUID uuid) {
        return playerDataCache.computeIfAbsent(uuid, PlayerTagData::new);
    }

    public void giveTag(UUID uuid, String tagId, long duration) {
        PlayerTagData data = getPlayerData(uuid);
        long expireTime = duration == -1 ? -1 : System.currentTimeMillis() + duration;
        data.addTag(tagId, expireTime);
        plugin.getDatabaseManager().addPlayerTag(uuid, tagId, expireTime);
    }

    public void removeTag(UUID uuid, String tagId) {
        PlayerTagData data = getPlayerData(uuid);
        data.removeTag(tagId);
        plugin.getDatabaseManager().removePlayerTag(uuid, tagId);
    }

    public void setCurrentTag(UUID uuid, String tagId) {
        PlayerTagData data = getPlayerData(uuid);
        data.setCurrentTag(tagId);
        plugin.getDatabaseManager().setSelectedTag(uuid, tagId);
    }

    public String getCurrentPrefix(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = data.getCurrentTag();
        if (currentTag == null || currentTag.isEmpty()) {
            currentTag = defaultTag;
        }

        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getPrefix() : "";
    }

    public String getCurrentSuffix(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = data.getCurrentTag();
        if (currentTag == null || currentTag.isEmpty()) {
            currentTag = defaultTag;
        }

        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getSuffix() : "";
    }

    public String getCurrentPrefix2(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = data.getCurrentTag();
        if (currentTag == null || currentTag.isEmpty()) {
            currentTag = defaultTag;
        }

        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getPrefix2() : "";
    }

    public String getCurrentSuffix2(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = data.getCurrentTag();
        if (currentTag == null || currentTag.isEmpty()) {
            currentTag = defaultTag;
        }

        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getSuffix2() : "";
    }

    public String getCurrentTagId(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = data.getCurrentTag();
        return currentTag != null ? currentTag : "";
    }

    public String getCurrentTagName(UUID uuid) {
        PlayerTagData data = playerDataCache.get(uuid);
        if (data == null) return "";

        String currentTag = data.getCurrentTag();
        if (currentTag == null || currentTag.isEmpty()) {
            return "";
        }

        Tag tag = tags.get(currentTag);
        return tag != null ? tag.getDisplayName() : "";
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
            plugin.getDatabaseManager().removePlayerTag(uuid, tagId);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.getMessageManager().send(player, "your-tag-expired", "tag", getTagDisplayName(tagId));
            }
        }

        if (data.getCurrentTag() == null && !expiredTags.isEmpty()) {
            plugin.getDatabaseManager().setSelectedTag(uuid, null);
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
        // 保存所有在线玩家数据
        for (Map.Entry<UUID, PlayerTagData> entry : playerDataCache.entrySet()) {
            plugin.getDatabaseManager().savePlayerData(entry.getValue());
        }
        playerDataCache.clear();
    }
}
