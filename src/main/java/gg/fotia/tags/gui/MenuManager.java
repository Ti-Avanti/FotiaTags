package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class MenuManager implements Listener {

    private final FotiaTags plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, MenuSession> openMenus = new HashMap<>();

    // 菜单配置
    private String menuTitle;
    private int menuSize;
    private String[] layout;
    private Map<Character, MenuItemConfig> items = new HashMap<>();
    private List<Integer> tagSlots = new ArrayList<>();
    private MenuItemConfig selectedTagItem;
    private MenuItemConfig unselectedTagItem;

    // 翻页按钮配置
    private int prevPageSlot = 45;
    private int nextPageSlot = 53;
    private MenuItemConfig prevPageItem;
    private MenuItemConfig nextPageItem;
    private MenuItemConfig prevPageDisabledItem;
    private MenuItemConfig nextPageDisabledItem;

    public MenuManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void loadMenus() {
        File menuFile = new File(plugin.getDataFolder(), "menus/tag-select.yml");
        if (!menuFile.exists()) {
            plugin.saveResource("menus/tag-select.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(menuFile);

        menuTitle = config.getString("title", "<!i>称号选择");
        menuSize = config.getInt("size", 54);

        // 加载布局
        List<String> layoutList = config.getStringList("layout");
        layout = layoutList.toArray(new String[0]);

        // 加载物品配置
        items.clear();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                if (key.length() == 1) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                    if (itemSection != null) {
                        items.put(key.charAt(0), loadMenuItemConfig(itemSection));
                    }
                }
            }
        }

        // 加载称号槽位
        tagSlots.clear();
        List<Integer> slots = config.getIntegerList("tag-slots");
        if (slots.isEmpty()) {
            // 默认槽位
            for (int i = 10; i <= 16; i++) tagSlots.add(i);
            for (int i = 19; i <= 25; i++) tagSlots.add(i);
            for (int i = 28; i <= 34; i++) tagSlots.add(i);
        } else {
            tagSlots.addAll(slots);
        }

        // 加载称号物品模板
        ConfigurationSection tagItemSection = config.getConfigurationSection("tag-item");
        if (tagItemSection != null) {
            ConfigurationSection selectedSection = tagItemSection.getConfigurationSection("selected");
            ConfigurationSection unselectedSection = tagItemSection.getConfigurationSection("unselected");

            if (selectedSection != null) {
                selectedTagItem = loadMenuItemConfig(selectedSection);
            }
            if (unselectedSection != null) {
                unselectedTagItem = loadMenuItemConfig(unselectedSection);
            }
        }

        // 默认模板
        if (selectedTagItem == null) {
            selectedTagItem = new MenuItemConfig();
            selectedTagItem.material = Material.PLAYER_HEAD;
            selectedTagItem.name = "<green><!i>%tag_name% <gray>(已选择)";
            selectedTagItem.lore = Arrays.asList("", "<gray>前缀: %tag_prefix%", "<gray>后缀: %tag_suffix%", "", "<yellow>点击取消选择");
            selectedTagItem.glow = true;
        }

        if (unselectedTagItem == null) {
            unselectedTagItem = new MenuItemConfig();
            unselectedTagItem.material = Material.PLAYER_HEAD;
            unselectedTagItem.name = "<white><!i>%tag_name%";
            unselectedTagItem.lore = Arrays.asList("", "<gray>前缀: %tag_prefix%", "<gray>后缀: %tag_suffix%", "<gray>过期时间: %tag_expire%", "", "<yellow>点击选择此称号");
            unselectedTagItem.glow = false;
        }

        // 加载翻页配置
        ConfigurationSection paginationSection = config.getConfigurationSection("pagination");
        if (paginationSection != null) {
            prevPageSlot = paginationSection.getInt("prev-page-slot", 45);
            nextPageSlot = paginationSection.getInt("next-page-slot", 53);

            ConfigurationSection prevSection = paginationSection.getConfigurationSection("prev-page");
            if (prevSection != null) {
                prevPageItem = loadMenuItemConfig(prevSection);
            }

            ConfigurationSection nextSection = paginationSection.getConfigurationSection("next-page");
            if (nextSection != null) {
                nextPageItem = loadMenuItemConfig(nextSection);
            }

            ConfigurationSection prevDisabledSection = paginationSection.getConfigurationSection("prev-page-disabled");
            if (prevDisabledSection != null) {
                prevPageDisabledItem = loadMenuItemConfig(prevDisabledSection);
            }

            ConfigurationSection nextDisabledSection = paginationSection.getConfigurationSection("next-page-disabled");
            if (nextDisabledSection != null) {
                nextPageDisabledItem = loadMenuItemConfig(nextDisabledSection);
            }
        }

        // 默认翻页按钮
        if (prevPageItem == null) {
            prevPageItem = new MenuItemConfig();
            prevPageItem.material = Material.ARROW;
            prevPageItem.name = "<yellow><!i>上一页";
            prevPageItem.lore = Arrays.asList("", "<gray>点击查看上一页");
        }

        if (nextPageItem == null) {
            nextPageItem = new MenuItemConfig();
            nextPageItem.material = Material.ARROW;
            nextPageItem.name = "<yellow><!i>下一页";
            nextPageItem.lore = Arrays.asList("", "<gray>点击查看下一页");
        }

        if (prevPageDisabledItem == null) {
            prevPageDisabledItem = new MenuItemConfig();
            prevPageDisabledItem.material = Material.GRAY_DYE;
            prevPageDisabledItem.name = "<gray><!i>上一页";
            prevPageDisabledItem.lore = Arrays.asList("", "<gray>已经是第一页");
        }

        if (nextPageDisabledItem == null) {
            nextPageDisabledItem = new MenuItemConfig();
            nextPageDisabledItem.material = Material.GRAY_DYE;
            nextPageDisabledItem.name = "<gray><!i>下一页";
            nextPageDisabledItem.lore = Arrays.asList("", "<gray>已经是最后一页");
        }

        plugin.getLogger().info("Menu configuration loaded!");
    }

    private MenuItemConfig loadMenuItemConfig(ConfigurationSection section) {
        MenuItemConfig config = new MenuItemConfig();
        config.material = Material.matchMaterial(section.getString("material", "STONE"));
        if (config.material == null) config.material = Material.STONE;
        config.name = section.getString("name", "");
        config.lore = section.getStringList("lore");
        config.itemModel = section.getString("item_model");
        config.tooltip = section.getString("tooltip");
        config.glow = section.getBoolean("glow", false);
        config.actions = section.getStringList("actions");
        return config;
    }

    public void openTagSelectMenu(Player player) {
        openTagSelectMenu(player, 0);
    }

    public void openTagSelectMenu(Player player, int page) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(player.getUniqueId());
        List<String> ownedTags = data != null ? data.getValidTags() : Collections.emptyList();
        String currentTag = data != null ? data.getCurrentTag() : null;

        // 过滤掉没有权限的称号
        List<String> availableTags = new ArrayList<>();
        for (String tagId : ownedTags) {
            Tag tag = plugin.getTagManager().getTag(tagId);
            if (tag != null && (!tag.hasPermission() || player.hasPermission(tag.getPermission()))) {
                availableTags.add(tagId);
            }
        }

        int totalTags = availableTags.size();
        int tagsPerPage = tagSlots.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalTags / tagsPerPage));

        // 确保页码有效
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        // 创建标题（包含页码）
        String title = menuTitle;
        if (totalPages > 1) {
            title = title + " <gray>(" + (page + 1) + "/" + totalPages + ")";
        }

        Inventory inventory = Bukkit.createInventory(null, menuSize, miniMessage.deserialize("<!i>" + title));

        // 填充布局物品
        for (int row = 0; row < layout.length && row < menuSize / 9; row++) {
            String rowLayout = layout[row];
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                char c = rowLayout.charAt(col);
                int slot = row * 9 + col;

                if (c == ' ') continue;

                MenuItemConfig itemConfig = items.get(c);
                if (itemConfig != null) {
                    inventory.setItem(slot, createMenuItem(itemConfig, player, null));
                }
            }
        }

        // 填充当前页的称号
        int startIndex = page * tagsPerPage;
        int tagIndex = 0;
        for (int slot : tagSlots) {
            int actualIndex = startIndex + tagIndex;
            if (actualIndex >= availableTags.size()) break;

            String tagId = availableTags.get(actualIndex);
            Tag tag = plugin.getTagManager().getTag(tagId);
            if (tag == null) {
                tagIndex++;
                continue;
            }

            boolean isSelected = tagId.equals(currentTag);
            MenuItemConfig template = isSelected ? selectedTagItem : unselectedTagItem;

            ItemStack item = createTagItem(template, tag, data, isSelected);
            inventory.setItem(slot, item);

            tagIndex++;
        }

        // 添加翻页按钮
        boolean hasPrevPage = page > 0;
        boolean hasNextPage = page < totalPages - 1;

        // 上一页按钮
        ItemStack prevItem = createMenuItem(hasPrevPage ? prevPageItem : prevPageDisabledItem, player, null);
        prevItem = setPageAction(prevItem, hasPrevPage ? "prev" : null);
        inventory.setItem(prevPageSlot, prevItem);

        // 下一页按钮
        ItemStack nextItem = createMenuItem(hasNextPage ? nextPageItem : nextPageDisabledItem, player, null);
        nextItem = setPageAction(nextItem, hasNextPage ? "next" : null);
        inventory.setItem(nextPageSlot, nextItem);

        // 保存会话
        openMenus.put(player.getUniqueId(), new MenuSession("tag-select", page));
        player.openInventory(inventory);
    }

    private ItemStack setPageAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && action != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "page_action"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    action
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getPageAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "page_action"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    private ItemStack createMenuItem(MenuItemConfig config, Player player, Tag tag) {
        ItemStack item = new ItemStack(config.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // 设置名称
        if (config.name != null && !config.name.isEmpty()) {
            String name = config.name;
            if (player != null) {
                name = name.replace("%player%", player.getName());
            }
            meta.displayName(miniMessage.deserialize("<!i>" + name));
        }

        // 设置lore
        if (config.lore != null && !config.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : config.lore) {
                if (player != null) {
                    line = line.replace("%player%", player.getName());
                }
                lore.add(miniMessage.deserialize("<!i>" + line));
            }
            meta.lore(lore);
        }

        // 设置item_model (1.21+ API，使用反射兼容)
        if (config.itemModel != null && !config.itemModel.isEmpty()) {
            setItemModelCompat(meta, config.itemModel);
        }

        // 设置tooltip (1.21+ API，使用反射兼容)
        if (config.tooltip != null && !config.tooltip.isEmpty()) {
            setTooltipStyleCompat(meta, config.tooltip);
        }

        // 设置发光
        if (config.glow) {
            setGlowCompat(meta);
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTagItem(MenuItemConfig template, Tag tag, PlayerTagData data, boolean isSelected) {
        // 使用称号自己的物品配置，如果没有则使用模板的
        Material material = tag.getMaterial() != null ? tag.getMaterial() : template.material;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // 替换占位符
        String name = template.name
                .replace("%tag_name%", tag.getDisplayName())
                .replace("%tag_id%", tag.getId());
        meta.displayName(miniMessage.deserialize("<!i>" + name));

        // 设置lore
        if (template.lore != null && !template.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            long expireTime = data != null ? data.getTagExpireTime(tag.getId()) : -1;
            String expireStr = TimeUtil.formatExpireTime(expireTime);

            for (String line : template.lore) {
                line = line.replace("%tag_name%", tag.getDisplayName())
                        .replace("%tag_id%", tag.getId())
                        .replace("%tag_prefix%", tag.getPrefix())
                        .replace("%tag_suffix%", tag.getSuffix())
                        .replace("%tag_expire%", expireStr);
                lore.add(miniMessage.deserialize("<!i>" + line));
            }
            meta.lore(lore);
        }

        // 优先使用称号自己的item-model，否则使用模板的
        String itemModel = tag.getItemModel();
        if (itemModel == null || itemModel.isEmpty()) {
            if (template.itemModel != null && !template.itemModel.isEmpty()) {
                itemModel = template.itemModel.replace("%tag_id%", tag.getId());
            }
        }
        if (itemModel != null && !itemModel.isEmpty()) {
            setItemModelCompat(meta, itemModel);
        }

        // 设置custom-model-data
        if (tag.getCustomModelData() > 0) {
            meta.setCustomModelData(tag.getCustomModelData());
        }

        // 设置tooltip (1.21+ API，使用反射兼容)
        if (template.tooltip != null && !template.tooltip.isEmpty()) {
            setTooltipStyleCompat(meta, template.tooltip);
        }

        // 设置发光
        if (template.glow || isSelected) {
            setGlowCompat(meta);
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        // 存储tag id到item
        item = setTagId(item, tag.getId());

        return item;
    }

    private ItemStack setTagId(ItemStack item, String tagId) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "tag_id"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    tagId
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getTagId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "tag_id"),
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MenuSession session = openMenus.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= menuSize) return;

        // 检查是否点击了翻页按钮
        String pageAction = getPageAction(clickedItem);
        if (pageAction != null) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if (pageAction.equals("prev")) {
                openTagSelectMenu(player, session.page - 1);
            } else if (pageAction.equals("next")) {
                openTagSelectMenu(player, session.page + 1);
            }
            return;
        }

        // 检查是否点击了称号物品
        String tagId = getTagId(clickedItem);
        if (tagId != null) {
            handleTagClick(player, tagId, session.page);
            return;
        }

        // 检查是否点击了布局物品
        int row = slot / 9;
        int col = slot % 9;
        if (row < layout.length && col < layout[row].length()) {
            char c = layout[row].charAt(col);
            MenuItemConfig itemConfig = items.get(c);
            if (itemConfig != null && itemConfig.actions != null) {
                executeActions(player, itemConfig.actions, session.page);
            }
        }
    }

    private void handleTagClick(Player player, String tagId, int currentPage) {
        PlayerTagData data = plugin.getTagManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String currentTag = data.getCurrentTag();

        if (tagId.equals(currentTag)) {
            // 取消选择
            plugin.getTagManager().setCurrentTag(player.getUniqueId(), null);
            plugin.getMessageManager().send(player, "your-tag-cleared");
        } else {
            // 选择称号
            plugin.getTagManager().setCurrentTag(player.getUniqueId(), tagId);
            Tag tag = plugin.getTagManager().getTag(tagId);
            String tagName = tag != null ? tag.getDisplayName() : tagId;
            plugin.getMessageManager().send(player, "your-tag-set",
                    Map.of("tag", tagName));
        }

        // 播放音效
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // 直接刷新当前页物品，不关闭GUI
        refreshTagItems(player, currentPage);
    }

    private void refreshTagItems(Player player, int page) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory == null) return;

        PlayerTagData data = plugin.getTagManager().getPlayerData(player.getUniqueId());
        List<String> ownedTags = data != null ? data.getValidTags() : Collections.emptyList();
        String currentTag = data != null ? data.getCurrentTag() : null;

        // 过滤掉没有权限的称号
        List<String> availableTags = new ArrayList<>();
        for (String tagId : ownedTags) {
            Tag tag = plugin.getTagManager().getTag(tagId);
            if (tag != null && (!tag.hasPermission() || player.hasPermission(tag.getPermission()))) {
                availableTags.add(tagId);
            }
        }

        int tagsPerPage = tagSlots.size();
        int startIndex = page * tagsPerPage;
        int tagIndex = 0;

        for (int slot : tagSlots) {
            int actualIndex = startIndex + tagIndex;
            if (actualIndex >= availableTags.size()) {
                // 清空多余槽位
                inventory.setItem(slot, null);
                tagIndex++;
                continue;
            }

            String tagId = availableTags.get(actualIndex);
            Tag tag = plugin.getTagManager().getTag(tagId);
            if (tag == null) {
                tagIndex++;
                continue;
            }

            boolean isSelected = tagId.equals(currentTag);
            MenuItemConfig template = isSelected ? selectedTagItem : unselectedTagItem;

            ItemStack item = createTagItem(template, tag, data, isSelected);
            inventory.setItem(slot, item);

            tagIndex++;
        }
    }

    private void executeActions(Player player, List<String> actions, int currentPage) {
        for (String action : actions) {
            action = action.trim();

            if (action.startsWith("[sound]") || action.startsWith("sound:")) {
                String sound = action.replace("[sound]", "").replace("sound:", "").trim();
                try {
                    String[] parts = sound.split("-");
                    org.bukkit.Sound soundEnum = org.bukkit.Sound.valueOf(parts[0].toUpperCase());
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    player.playSound(player.getLocation(), soundEnum, volume, pitch);
                } catch (Exception ignored) {}
            } else if (action.equals("[close]") || action.equals("close")) {
                player.closeInventory();
            } else if (action.equals("clear-tag") || action.equals("[clear-tag]")) {
                // 清除称号但不关闭GUI
                plugin.getTagManager().setCurrentTag(player.getUniqueId(), null);
                plugin.getMessageManager().send(player, "your-tag-cleared");
                refreshTagItems(player, currentPage);
            } else if (action.startsWith("[command]") || action.startsWith("command:")) {
                String command = action.replace("[command]", "").replace("command:", "").trim()
                        .replace("%player%", player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
            } else if (action.startsWith("[console]") || action.startsWith("console:")) {
                String command = action.replace("[console]", "").replace("console:", "").trim()
                        .replace("%player%", player.getName());
                Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
            } else if (action.startsWith("[message]") || action.startsWith("message:")) {
                String message = action.replace("[message]", "").replace("message:", "").trim();
                player.sendMessage(miniMessage.deserialize("<!i>" + message));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }

    // 兼容1.20.4的方法 - setItemModel在1.21+才有
    private void setItemModelCompat(ItemMeta meta, String itemModel) {
        try {
            NamespacedKey key = NamespacedKey.fromString(itemModel);
            if (key != null) {
                java.lang.reflect.Method method = meta.getClass().getMethod("setItemModel", NamespacedKey.class);
                method.invoke(meta, key);
            }
        } catch (Exception ignored) {
            // 1.20.4不支持此API，忽略
        }
    }

    // 兼容1.20.4的方法 - setTooltipStyle在1.21+才有
    private void setTooltipStyleCompat(ItemMeta meta, String tooltip) {
        try {
            NamespacedKey key = NamespacedKey.fromString(tooltip);
            if (key != null) {
                java.lang.reflect.Method method = meta.getClass().getMethod("setTooltipStyle", NamespacedKey.class);
                method.invoke(meta, key);
            }
        } catch (Exception ignored) {
            // 1.20.4不支持此API，忽略
        }
    }

    // 兼容1.20.4的方法 - setEnchantmentGlintOverride在1.21+才有
    private void setGlowCompat(ItemMeta meta) {
        try {
            // 尝试1.21+ API
            java.lang.reflect.Method method = meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class);
            method.invoke(meta, true);
        } catch (Exception e) {
            // 1.20.4使用附魔+隐藏附魔标志实现发光
            try {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } catch (Exception ignored) {}
        }
    }

    private static class MenuItemConfig {
        Material material = Material.STONE;
        String name = "";
        List<String> lore = new ArrayList<>();
        String itemModel;
        String tooltip;
        boolean glow = false;
        List<String> actions = new ArrayList<>();
    }

    private static class MenuSession {
        String menuType;
        int page;

        MenuSession(String menuType, int page) {
            this.menuType = menuType;
            this.page = page;
        }
    }
}
