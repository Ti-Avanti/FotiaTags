package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.storage.PlayerProfile;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.tag.TagManager;
import gg.fotia.tags.util.LegacyColorConverter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerTagAdminManager implements Listener {

    private final FotiaTags plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, MenuSession> openMenus = new HashMap<>();
    private final Set<UUID> reopeningMenus = new HashSet<>();
    private final NamespacedKey actionKey;
    private final NamespacedKey playerKey;
    private final NamespacedKey playerNameKey;
    private final NamespacedKey tagKey;
    private final boolean modernItemMetaApi;

    private PlayerListMenu playerListMenu;
    private PlayerTagMenu playerTagMenu;

    public PlayerTagAdminManager(FotiaTags plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "admin_action");
        this.playerKey = new NamespacedKey(plugin, "admin_player_uuid");
        this.playerNameKey = new NamespacedKey(plugin, "admin_player_name");
        this.tagKey = new NamespacedKey(plugin, "admin_tag_id");
        this.modernItemMetaApi = isAtLeastMinecraftVersion(1, 21, 4);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        this.playerListMenu = loadPlayerListMenu();
        this.playerTagMenu = loadPlayerTagMenu();
    }

    public void openPlayerList(Player admin) {
        openPlayerList(admin, 0);
    }

    public void openPlayerList(Player admin, int page) {
        plugin.getDatabaseManager().loadPlayerProfiles()
                .whenComplete((profiles, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        plugin.getMessageManager().send(admin, "admin-player-list-load-failed");
                        return;
                    }
                    openPlayerListLoaded(admin, mergeOnlineProfiles(profiles), page);
                }));
    }

    public void openPlayerTags(Player admin, UUID targetUuid, String targetName) {
        openPlayerTags(admin, targetUuid, targetName, 0);
    }

    public void openPlayerTags(Player admin, UUID targetUuid, String targetName, int page) {
        plugin.getDatabaseManager().loadPlayerData(targetUuid)
                .whenComplete((data, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        plugin.getMessageManager().send(admin, "admin-player-tags-load-failed",
                                MessageManager.of("player", targetName));
                        return;
                    }
                    openPlayerTagsLoaded(admin, targetUuid, targetName, data, page);
                }));
    }

    private List<PlayerProfile> mergeOnlineProfiles(List<PlayerProfile> loadedProfiles) {
        Map<UUID, PlayerProfile> profiles = new LinkedHashMap<>();
        for (PlayerProfile profile : loadedProfiles) {
            profiles.put(profile.uuid(), profile);
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID uuid = onlinePlayer.getUniqueId();
            plugin.getDatabaseManager().savePlayerProfile(uuid, onlinePlayer.getName());
            PlayerProfile existing = profiles.get(uuid);
            if (existing != null) {
                profiles.put(uuid, new PlayerProfile(uuid, onlinePlayer.getName(), existing.currentTag(),
                        existing.ownedTagCount(), existing.hasCustomTag()));
                continue;
            }

            PlayerTagData data = plugin.getTagManager().getPlayerData(uuid);
            profiles.put(uuid, new PlayerProfile(
                    uuid,
                    onlinePlayer.getName(),
                    data != null ? data.getCurrentTag() : null,
                    data != null ? data.getValidTags().size() : 0,
                    data != null && data.hasCustomTag()
            ));
        }

        return profiles.values().stream()
                .sorted(Comparator.comparing(profile -> profile.displayName().toLowerCase()))
                .toList();
    }

    private void openPlayerListLoaded(Player admin, List<PlayerProfile> profiles, int page) {
        int totalItems = profiles.size();
        int itemsPerPage = Math.max(1, playerListMenu.playerSlots().size());
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        int safePage = clampPage(page, totalPages);

        Map<String, String> placeholders = basePlaceholders(admin, safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(null, playerListMenu.size(), parse(apply(placeholders, playerListMenu.title())));
        renderLayout(inventory, playerListMenu.layout(), playerListMenu.items(), placeholders);

        int start = safePage * itemsPerPage;
        for (int i = 0; i < playerListMenu.playerSlots().size(); i++) {
            int profileIndex = start + i;
            if (profileIndex >= profiles.size()) {
                break;
            }
            int slot = playerListMenu.playerSlots().get(i);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, createPlayerItem(profiles.get(profileIndex)));
            }
        }

        openManagedInventory(admin, inventory, new MenuSession(MenuType.PLAYER_LIST, safePage, null, null));
    }

    private void openPlayerTagsLoaded(Player admin, UUID targetUuid, String targetName, PlayerTagData data, int page) {
        List<TagEntry> entries = buildTagEntries(data);
        int totalItems = entries.size();
        int itemsPerPage = Math.max(1, playerTagMenu.tagSlots().size());
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
        int safePage = clampPage(page, totalPages);

        Map<String, String> placeholders = basePlaceholders(admin, safePage, totalPages);
        placeholders.put("target_player", targetName);
        placeholders.put("player", targetName);
        placeholders.put("uuid", targetUuid.toString());
        placeholders.put("current_tag", displayCurrentTag(data));
        placeholders.put("tag_count", String.valueOf(data.getValidTags().size()));

        Inventory inventory = Bukkit.createInventory(null, playerTagMenu.size(), parse(apply(placeholders, playerTagMenu.title())));
        renderLayout(inventory, playerTagMenu.layout(), playerTagMenu.items(), placeholders);

        int start = safePage * itemsPerPage;
        for (int i = 0; i < playerTagMenu.tagSlots().size(); i++) {
            int entryIndex = start + i;
            if (entryIndex >= entries.size()) {
                break;
            }
            int slot = playerTagMenu.tagSlots().get(i);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, createTagItem(entries.get(entryIndex), targetName));
            }
        }

        openManagedInventory(admin, inventory, new MenuSession(MenuType.PLAYER_TAGS, safePage, targetUuid, targetName));
    }

    private List<TagEntry> buildTagEntries(PlayerTagData data) {
        List<TagEntry> entries = new ArrayList<>();
        String currentTag = data.getCurrentTag();
        List<String> ownedTagIds = new ArrayList<>(data.getValidTags());
        ownedTagIds.sort(String.CASE_INSENSITIVE_ORDER);

        for (String tagId : ownedTagIds) {
            Tag tag = plugin.getTagManager().getTag(tagId);
            if (tag == null) {
                entries.add(new TagEntry(tagId, tagId, "", "", "", Material.BARRIER, 0, "", "",
                        tagId.equals(currentTag), false));
                continue;
            }

            entries.add(new TagEntry(
                    tagId,
                    tag.getDisplayName(),
                    tag.getPrefix(),
                    tag.getSuffix(),
                    plugin.getMessageManager().formatExpireTime(data.getTagExpireTime(tagId)),
                    tag.getMaterial() != null ? tag.getMaterial() : Material.NAME_TAG,
                    tag.getCustomModelData(),
                    tag.getItemModel(),
                    tag.getTooltipStyle(),
                    tagId.equals(currentTag),
                    false
            ));
        }

        for (CustomTag customTag : data.getCustomTags().values()) {
            if (customTag == null || !customTag.isComplete()) {
                continue;
            }
            CustomTagManager.CustomTagIcon icon = plugin.getCustomTagManager().getIcon(customTag.getIconId());
            entries.add(new TagEntry(
                    customTag.getId(),
                    plugin.getCustomTagManager().getDisplayName(),
                    customTag.getPrefix(),
                    customTag.getSuffix(),
                    plugin.getMessageManager().formatExpireTime(-1),
                    icon != null ? icon.material() : Material.NAME_TAG,
                    icon != null ? icon.customModelData() : 0,
                    icon != null ? icon.itemModel() : "",
                    icon != null ? icon.tooltipStyle() : "",
                    customTag.getId().equals(currentTag),
                    true
            ));
        }

        return entries;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }

        MenuSession session = openMenus.get(admin.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }

        String playerUuid = getString(item, playerKey);
        if (playerUuid != null) {
            String playerName = getString(item, playerNameKey);
            admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openPlayerTags(admin, UUID.fromString(playerUuid), playerName);
            return;
        }

        String tagId = getString(item, tagKey);
        if (tagId != null && session.targetUuid() != null) {
            admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            handleTagClick(admin, session, tagId, event.getClick());
            return;
        }

        String action = getString(item, actionKey);
        if (action != null) {
            admin.playSound(admin.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            handleAction(admin, session, action);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (!reopeningMenus.contains(uuid)) {
                openMenus.remove(uuid);
            }
        }
    }

    private void handleAction(Player admin, MenuSession session, String action) {
        switch (action.toLowerCase()) {
            case "prev-page" -> openSessionPage(admin, session, session.page() - 1);
            case "next-page" -> openSessionPage(admin, session, session.page() + 1);
            case "back" -> openPlayerList(admin, 0);
            case "clear-current" -> {
                if (session.targetUuid() == null) {
                    return;
                }
                completeAndReopen(admin,
                        plugin.getTagManager().setCurrentTagAsync(session.targetUuid(), null),
                        "admin-player-tag-cleared",
                        session);
            }
            case "close" -> admin.closeInventory();
            default -> {
            }
        }
    }

    private void handleTagClick(Player admin, MenuSession session, String tagId, ClickType clickType) {
        boolean customTag = plugin.getTagManager().isCustomTagId(tagId);
        if (clickType.isRightClick()) {
            CompletableFuture<Void> future = customTag
                    ? plugin.getTagManager().removeCustomTagAsync(session.targetUuid(), tagId)
                    : plugin.getTagManager().removeTagAsync(session.targetUuid(), tagId);
            completeAndReopen(admin, future,
                    customTag ? "admin-player-custom-tag-removed" : "admin-player-tag-removed",
                    session,
                    MessageManager.of("tag", displayTagName(tagId), "player", session.targetName()));
            return;
        }

        if (customTag && !plugin.getCustomTagManager().isEnabled()) {
            plugin.getMessageManager().send(admin, "custom-tag-disabled");
            return;
        }

        plugin.getDatabaseManager().loadPlayerData(session.targetUuid())
                .whenComplete((data, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        plugin.getMessageManager().send(admin, "admin-player-tags-load-failed",
                                MessageManager.of("player", session.targetName()));
                        return;
                    }
                    String nextTag = tagId.equals(data.getCurrentTag()) ? null : tagId;
                    completeAndReopen(admin,
                            plugin.getTagManager().setCurrentTagAsync(session.targetUuid(), nextTag),
                            nextTag == null ? "admin-player-tag-cleared" : "admin-player-tag-selected",
                            session,
                            MessageManager.of("tag", displayTagName(tagId), "player", session.targetName()));
                }));
    }

    private void completeAndReopen(Player admin, CompletableFuture<Void> future, String successKey, MenuSession session) {
        completeAndReopen(admin, future, successKey, session, MessageManager.of("player", session.targetName()));
    }

    private void completeAndReopen(Player admin, CompletableFuture<Void> future, String successKey, MenuSession session, Map<String, String> placeholders) {
        future.whenComplete((ignored, throwable) -> runSync(() -> {
            if (throwable != null) {
                plugin.getMessageManager().send(admin, "admin-player-tags-save-failed",
                        MessageManager.of("player", session.targetName()));
                return;
            }
            plugin.getMessageManager().send(admin, successKey, placeholders);
            openPlayerTags(admin, session.targetUuid(), session.targetName(), session.page());
        }));
    }

    private void openSessionPage(Player admin, MenuSession session, int page) {
        if (session.type() == MenuType.PLAYER_LIST) {
            openPlayerList(admin, page);
            return;
        }
        if (session.targetUuid() != null) {
            openPlayerTags(admin, session.targetUuid(), session.targetName(), page);
        }
    }

    private PlayerListMenu loadPlayerListMenu() {
        File file = menuFile("menus/player-manager-list.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        AdminMenu fallback = defaultPlayerListMenu();
        int size = normalizeMenuSize(config.getInt("size", fallback.size()));
        List<Integer> playerSlots = cleanSlots(config.getIntegerList("player-slots"), size);
        if (playerSlots.isEmpty()) {
            playerSlots = defaultPlayerSlots();
        }

        return new PlayerListMenu(
                config.getString("title", fallback.title()),
                size,
                loadLayout(config, fallback.layout()),
                loadItems(config.getConfigurationSection("items"), fallback.items()),
                playerSlots,
                loadMenuItem(config.getConfigurationSection("player-item"), defaultPlayerItem())
        );
    }

    private PlayerTagMenu loadPlayerTagMenu() {
        File file = menuFile("menus/player-manager-tags.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        AdminMenu fallback = defaultPlayerTagMenu();
        int size = normalizeMenuSize(config.getInt("size", fallback.size()));
        List<Integer> tagSlots = cleanSlots(config.getIntegerList("tag-slots"), size);
        if (tagSlots.isEmpty()) {
            tagSlots = defaultTagSlots();
        }

        ConfigurationSection tagItemSection = config.getConfigurationSection("tag-item");
        AdminMenuItem selected = defaultSelectedTagItem();
        AdminMenuItem unselected = defaultUnselectedTagItem();
        if (tagItemSection != null) {
            selected = loadMenuItem(tagItemSection.getConfigurationSection("selected"), selected);
            unselected = loadMenuItem(tagItemSection.getConfigurationSection("unselected"), unselected);
        }
        ConfigurationSection textSection = config.getConfigurationSection("placeholder-texts");

        return new PlayerTagMenu(
                config.getString("title", fallback.title()),
                size,
                loadLayout(config, fallback.layout()),
                loadItems(config.getConfigurationSection("items"), fallback.items()),
                tagSlots,
                selected,
                unselected,
                getText(textSection, "none", "<gray>无"),
                getText(textSection, "yes", "<green>有"),
                getText(textSection, "no", "<red>无"),
                getText(textSection, "custom", "<light_purple>自定义"),
                getText(textSection, "normal", "<aqua>普通")
        );
    }

    private String getText(ConfigurationSection section, String key, String fallback) {
        return section != null ? section.getString(key, fallback) : fallback;
    }

    private File menuFile(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        return file;
    }

    private List<String> loadLayout(YamlConfiguration config, List<String> fallback) {
        List<String> layout = config.getStringList("layout");
        return layout.isEmpty() ? fallback : layout;
    }

    private Map<Character, AdminMenuItem> loadItems(ConfigurationSection section, Map<Character, AdminMenuItem> fallback) {
        Map<Character, AdminMenuItem> items = new HashMap<>(fallback);
        if (section == null) {
            return items;
        }

        for (String key : section.getKeys(false)) {
            if (key.length() != 1) {
                continue;
            }
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection != null) {
                items.put(key.charAt(0), loadMenuItem(itemSection, items.get(key.charAt(0))));
            }
        }
        return items;
    }

    private AdminMenuItem loadMenuItem(ConfigurationSection section, AdminMenuItem fallback) {
        if (section == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(section.getString("material", fallback.material().name()));
        if (material == null) {
            material = fallback.material();
        }
        return new AdminMenuItem(
                material,
                section.getString("name", fallback.name()),
                section.contains("lore") ? section.getStringList("lore") : fallback.lore(),
                section.getString("action", fallback.action()),
                section.getString("item-model", section.getString("item_model", fallback.itemModel())),
                section.getString("tooltip-style", section.getString("tooltip", fallback.tooltipStyle())),
                section.getBoolean("glow", fallback.glow()),
                section.getBoolean("use-player-head", fallback.usePlayerHead())
        );
    }

    private ItemStack createPlayerItem(PlayerProfile profile) {
        AdminMenuItem template = playerListMenu.playerItem();
        ItemStack item = new ItemStack(template.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (template.usePlayerHead() && meta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(profile.uuid());
            skullMeta.setOwningPlayer(offlinePlayer);
            meta = skullMeta;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", profile.displayName());
        placeholders.put("uuid", profile.uuid().toString());
        placeholders.put("current_tag", displayTagName(profile.currentTag()));
        placeholders.put("tag_count", String.valueOf(profile.ownedTagCount()));
        placeholders.put("custom_tag", profile.hasCustomTag() ? playerTagMenu.yesText() : playerTagMenu.noText());
        applyItem(meta, template, placeholders);
        meta.getPersistentDataContainer().set(playerKey, PersistentDataType.STRING, profile.uuid().toString());
        meta.getPersistentDataContainer().set(playerNameKey, PersistentDataType.STRING, profile.displayName());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTagItem(TagEntry entry, String targetName) {
        AdminMenuItem template = entry.selected() ? playerTagMenu.selectedTagItem() : playerTagMenu.unselectedTagItem();
        ItemStack item = new ItemStack(entry.material() != null ? entry.material() : template.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", targetName);
        placeholders.put("tag_id", entry.id());
        placeholders.put("tag_name", LegacyColorConverter.convertToMiniMessage(entry.displayName()));
        placeholders.put("tag_prefix", LegacyColorConverter.convertToMiniMessage(entry.prefix()));
        placeholders.put("tag_suffix", LegacyColorConverter.convertToMiniMessage(entry.suffix()));
        placeholders.put("tag_expire", entry.expireText());
        placeholders.put("tag_type", entry.custom() ? playerTagMenu.customText() : playerTagMenu.normalText());
        placeholders.put("selected", entry.selected() ? playerTagMenu.yesText() : playerTagMenu.noText());
        applyItem(meta, template, placeholders);

        if (entry.customModelData() > 0) {
            meta.setCustomModelData(entry.customModelData());
        }
        String itemModel = entry.itemModel() != null && !entry.itemModel().isEmpty() ? entry.itemModel() : template.itemModel();
        if (itemModel != null && !itemModel.isEmpty()) {
            setItemModelCompat(meta, itemModel.replace("%tag_id%", entry.id()));
        }
        String tooltipStyle = entry.tooltipStyle() != null && !entry.tooltipStyle().isEmpty() ? entry.tooltipStyle() : template.tooltipStyle();
        if (tooltipStyle != null && !tooltipStyle.isEmpty()) {
            setTooltipStyleCompat(meta, tooltipStyle);
        }
        if (template.glow() || entry.selected()) {
            setGlowCompat(meta);
        }

        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, entry.id());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void renderLayout(Inventory inventory, List<String> layout, Map<Character, AdminMenuItem> items, Map<String, String> placeholders) {
        for (int row = 0; row < layout.size() && row < inventory.getSize() / 9; row++) {
            String rowLayout = layout.get(row);
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                char key = rowLayout.charAt(col);
                if (key == ' ') {
                    continue;
                }
                AdminMenuItem itemConfig = items.get(key);
                if (itemConfig == null) {
                    continue;
                }
                inventory.setItem(row * 9 + col, createStaticItem(itemConfig, placeholders));
            }
        }
    }

    private ItemStack createStaticItem(AdminMenuItem template, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(template.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        applyItem(meta, template, placeholders);
        if (template.action() != null && !template.action().isBlank()) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, template.action());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void applyItem(ItemMeta meta, AdminMenuItem template, Map<String, String> placeholders) {
        meta.displayName(parse(apply(placeholders, template.name())));
        if (!template.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : template.lore()) {
                String parsed = apply(placeholders, line);
                if (!parsed.isEmpty()) {
                    lore.add(parse(parsed));
                }
            }
            meta.lore(lore);
        }
        if (template.itemModel() != null && !template.itemModel().isEmpty()) {
            setItemModelCompat(meta, apply(placeholders, template.itemModel()));
        }
        if (template.tooltipStyle() != null && !template.tooltipStyle().isEmpty()) {
            setTooltipStyleCompat(meta, apply(placeholders, template.tooltipStyle()));
        }
        if (template.glow()) {
            setGlowCompat(meta);
        }
        meta.addItemFlags(ItemFlag.values());
    }

    private Map<String, String> basePlaceholders(Player admin, int page, int totalPages) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("admin", admin.getName());
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("total_pages", String.valueOf(totalPages));
        return placeholders;
    }

    private String apply(Map<String, String> placeholders, String text) {
        String result = text != null ? text : "";
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    private String displayCurrentTag(PlayerTagData data) {
        return displayTagName(data.getCurrentTag());
    }

    private String displayTagName(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return playerTagMenu.noneText();
        }
        if (plugin.getTagManager().isCustomTagId(tagId)) {
            return LegacyColorConverter.convertToMiniMessage(plugin.getCustomTagManager().getDisplayName());
        }
        Tag tag = plugin.getTagManager().getTag(tagId);
        return tag != null ? LegacyColorConverter.convertToMiniMessage(tag.getDisplayName()) : tagId;
    }

    private Component parse(String text) {
        return miniMessage.deserialize("<!i>" + LegacyColorConverter.convertToMiniMessage(text));
    }

    private String getString(ItemStack item, NamespacedKey key) {
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private void openManagedInventory(Player player, Inventory inventory, MenuSession session) {
        UUID uuid = player.getUniqueId();
        reopeningMenus.add(uuid);
        try {
            openMenus.put(uuid, session);
            player.openInventory(inventory);
        } finally {
            reopeningMenus.remove(uuid);
        }
    }

    private void runSync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private int clampPage(int page, int totalPages) {
        if (page < 0) {
            return 0;
        }
        if (page >= totalPages) {
            return totalPages - 1;
        }
        return page;
    }

    private List<Integer> cleanSlots(List<Integer> slots, int menuSize) {
        List<Integer> clean = new ArrayList<>();
        for (int slot : slots) {
            if (slot >= 0 && slot < menuSize) {
                clean.add(slot);
            }
        }
        return clean;
    }

    private int normalizeMenuSize(int size) {
        if (size < 9) {
            return 9;
        }
        if (size > 54) {
            return 54;
        }
        return ((size + 8) / 9) * 9;
    }

    private boolean isAtLeastMinecraftVersion(int major, int minor, int patch) {
        String version = Bukkit.getBukkitVersion().split("-", 2)[0];
        String[] parts = version.split("\\.");
        int actualMajor = parts.length > 0 ? parseVersionPart(parts[0]) : 0;
        int actualMinor = parts.length > 1 ? parseVersionPart(parts[1]) : 0;
        int actualPatch = parts.length > 2 ? parseVersionPart(parts[2]) : 0;
        if (actualMajor != major) {
            return actualMajor > major;
        }
        if (actualMinor != minor) {
            return actualMinor > minor;
        }
        return actualPatch >= patch;
    }

    private int parseVersionPart(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
    }

    private void setItemModelCompat(ItemMeta meta, String itemModel) {
        if (!modernItemMetaApi) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(itemModel);
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    private void setTooltipStyleCompat(ItemMeta meta, String tooltipStyle) {
        if (!modernItemMetaApi) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(tooltipStyle);
        if (key != null) {
            meta.setTooltipStyle(key);
        }
    }

    private void setGlowCompat(ItemMeta meta) {
        if (modernItemMetaApi) {
            meta.setEnchantmentGlintOverride(true);
            return;
        }
        Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (unbreaking != null) {
            meta.addEnchant(unbreaking, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    private AdminMenu defaultPlayerListMenu() {
        Map<Character, AdminMenuItem> items = new HashMap<>();
        items.put('X', new AdminMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", "", "", false, false));
        items.put('P', new AdminMenuItem(Material.ARROW, "<yellow><!i>上一页", List.of("<gray>当前: %page%/%total_pages%"), "prev-page", "", "", false, false));
        items.put('N', new AdminMenuItem(Material.ARROW, "<yellow><!i>下一页", List.of("<gray>当前: %page%/%total_pages%"), "next-page", "", "", false, false));
        items.put('C', new AdminMenuItem(Material.BARRIER, "<red><!i>关闭", List.of("<gray>点击关闭菜单"), "close", "", "", false, false));
        return new AdminMenu("<gold><!i>玩家称号管理 <gray>(%page%/%total_pages%)", 54,
                List.of("XXXXXXXXX", "X       X", "X       X", "X       X", "X       X", "PXXXCXXXN"), items);
    }

    private AdminMenu defaultPlayerTagMenu() {
        Map<Character, AdminMenuItem> items = new HashMap<>();
        items.put('X', new AdminMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", "", "", false, false));
        items.put('B', new AdminMenuItem(Material.ARROW, "<yellow><!i>返回玩家列表", List.of("<gray>点击返回"), "back", "", "", false, false));
        items.put('P', new AdminMenuItem(Material.ARROW, "<yellow><!i>上一页", List.of("<gray>当前: %page%/%total_pages%"), "prev-page", "", "", false, false));
        items.put('N', new AdminMenuItem(Material.ARROW, "<yellow><!i>下一页", List.of("<gray>当前: %page%/%total_pages%"), "next-page", "", "", false, false));
        items.put('C', new AdminMenuItem(Material.REDSTONE, "<red><!i>清空当前称号", List.of("<gray>当前: %current_tag%", "<yellow>点击清空"), "clear-current", "", "", false, false));
        return new AdminMenu("<gold><!i>%player% 的称号 <gray>(%page%/%total_pages%)", 54,
                List.of("XXXXXXXXX", "X       X", "X       X", "X       X", "X       X", "BXXPCXXN"), items);
    }

    private AdminMenuItem defaultPlayerItem() {
        return new AdminMenuItem(Material.PLAYER_HEAD, "<yellow><!i>%player%",
                List.of("<gray>UUID: %uuid%", "<gray>当前称号: %current_tag%", "<gray>普通称号: %tag_count%", "<gray>自定义称号: %custom_tag%", "", "<yellow>点击管理"),
                "", "", "", false, true);
    }

    private AdminMenuItem defaultSelectedTagItem() {
        return new AdminMenuItem(Material.NAME_TAG, "<green><!i>%tag_name% <gray>(已佩戴)",
                List.of("<gray>ID: %tag_id%", "<gray>类型: %tag_type%", "<gray>前缀: %tag_prefix%", "<gray>后缀: %tag_suffix%", "<gray>过期: %tag_expire%", "", "<yellow>左键取消佩戴", "<red>右键删除"),
                "", "", "", true, false);
    }

    private AdminMenuItem defaultUnselectedTagItem() {
        return new AdminMenuItem(Material.NAME_TAG, "<white><!i>%tag_name%",
                List.of("<gray>ID: %tag_id%", "<gray>类型: %tag_type%", "<gray>前缀: %tag_prefix%", "<gray>后缀: %tag_suffix%", "<gray>过期: %tag_expire%", "", "<yellow>左键佩戴", "<red>右键删除"),
                "", "", "", false, false);
    }

    private List<Integer> defaultTagSlots() {
        return List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
    }

    private List<Integer> defaultPlayerSlots() {
        return defaultTagSlots();
    }

    private enum MenuType {
        PLAYER_LIST,
        PLAYER_TAGS
    }

    private record MenuSession(MenuType type, int page, UUID targetUuid, String targetName) {
    }

    private record AdminMenu(String title, int size, List<String> layout, Map<Character, AdminMenuItem> items) {
    }

    private record PlayerListMenu(
            String title,
            int size,
            List<String> layout,
            Map<Character, AdminMenuItem> items,
            List<Integer> playerSlots,
            AdminMenuItem playerItem
    ) {
    }

    private record PlayerTagMenu(
            String title,
            int size,
            List<String> layout,
            Map<Character, AdminMenuItem> items,
            List<Integer> tagSlots,
            AdminMenuItem selectedTagItem,
            AdminMenuItem unselectedTagItem,
            String noneText,
            String yesText,
            String noText,
            String customText,
            String normalText
    ) {
    }

    private record AdminMenuItem(
            Material material,
            String name,
            List<String> lore,
            String action,
            String itemModel,
            String tooltipStyle,
            boolean glow,
            boolean usePlayerHead
    ) {
    }

    private record TagEntry(
            String id,
            String displayName,
            String prefix,
            String suffix,
            String expireText,
            Material material,
            int customModelData,
            String itemModel,
            String tooltipStyle,
            boolean selected,
            boolean custom
    ) {
    }
}
