package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.tag.TagManager;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.TextComponentParser;
import net.kyori.adventure.text.Component;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class MenuManager implements Listener {

    private final FotiaTags plugin;
    private final Map<UUID, MenuSession> openMenus = new HashMap<>();
    private final Set<UUID> reopeningMenus = new HashSet<>();

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
    private ActionMenuConfig customTagDetailMenu;
    private ActionMenuConfig customTagDeleteMenu;

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
        menuSize = normalizeMenuSize(config.getInt("size", 54));

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
            addDefaultTagSlots();
        } else {
            for (int slot : slots) {
                if (isValidSlot(slot)) {
                    tagSlots.add(slot);
                }
            }
            if (tagSlots.isEmpty()) {
                addDefaultTagSlots();
            }
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
            prevPageSlot = normalizeSlot(paginationSection.getInt("prev-page-slot", 45), Math.min(45, menuSize - 1));
            nextPageSlot = normalizeSlot(paginationSection.getInt("next-page-slot", 53), menuSize - 1);

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

        customTagDetailMenu = loadActionMenu("menus/custom-tag-detail.yml", defaultCustomTagDetailMenu());
        customTagDeleteMenu = loadActionMenu("menus/custom-tag-delete.yml", defaultCustomTagDeleteMenu());

        plugin.getLogger().info("Menu configuration loaded!");
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

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < menuSize;
    }

    private int normalizeSlot(int slot, int fallback) {
        return isValidSlot(slot) ? slot : Math.max(0, Math.min(fallback, menuSize - 1));
    }

    private void addDefaultTagSlots() {
        for (int i = 10; i <= 16; i++) addTagSlotIfValid(i);
        for (int i = 19; i <= 25; i++) addTagSlotIfValid(i);
        for (int i = 28; i <= 34; i++) addTagSlotIfValid(i);
        if (tagSlots.isEmpty()) {
            for (int i = 0; i < menuSize; i++) {
                tagSlots.add(i);
            }
        }
    }

    private void addTagSlotIfValid(int slot) {
        if (isValidSlot(slot)) {
            tagSlots.add(slot);
        }
    }

    public void openCustomTagDetailMenu(Player player, String customTagId, int page) {
        if (!plugin.getCustomTagManager().isEnabled()) {
            plugin.getMessageManager().send(player, "custom-tag-disabled");
            return;
        }

        CustomTag customTag = plugin.getTagManager().getCustomTag(player.getUniqueId(), customTagId);
        if (customTag == null) {
            plugin.getMessageManager().send(player, "custom-tag-not-purchased");
            openTagSelectMenu(player, page);
            return;
        }

        if (plugin.getFutureMenus().open(player, "custom-tag-detail", page, customTagId)) return;
        openActionMenu(player, customTagDetailMenu, customTag, page, "custom-detail");
    }

    public void openCustomTagDeleteMenu(Player player, String customTagId, int page) {
        if (!plugin.getCustomTagManager().isDeleteEnabled()) {
            plugin.getMessageManager().send(player, "custom-tag-delete-disabled");
            openCustomTagDetailMenu(player, customTagId, page);
            return;
        }

        CustomTag customTag = plugin.getTagManager().getCustomTag(player.getUniqueId(), customTagId);
        if (customTag == null) {
            plugin.getMessageManager().send(player, "custom-tag-not-purchased");
            openTagSelectMenu(player, page);
            return;
        }

        if (plugin.getFutureMenus().open(player, "custom-tag-delete", page, customTagId)) return;
        openActionMenu(player, customTagDeleteMenu, customTag, page, "custom-delete");
    }

    private void openActionMenu(Player player, ActionMenuConfig menu, CustomTag customTag, int page, String menuType) {
        String title = applyCustomPlaceholders(menu.title, customTag);
        Inventory inventory = Bukkit.createInventory(null, menu.size, TextComponentParser.parse(title));

        for (int row = 0; row < menu.layout.size() && row < menu.size / 9; row++) {
            String rowLayout = menu.layout.get(row);
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                char key = rowLayout.charAt(col);
                if (key == ' ') {
                    continue;
                }
                MenuItemConfig itemConfig = menu.items.get(key);
                if (itemConfig == null || shouldHideCustomActionItem(itemConfig)) {
                    continue;
                }
                ItemStack item = createCustomActionItem(itemConfig, customTag);
                if (item != null) {
                    inventory.setItem(row * 9 + col, item);
                }
            }
        }

        UUID uuid = player.getUniqueId();
        reopeningMenus.add(uuid);
        try {
            openMenus.put(uuid, new MenuSession(menuType, page, customTag.getId(), inventory));
            player.openInventory(inventory);
        } finally {
            reopeningMenus.remove(uuid);
        }
    }

    private boolean shouldHideCustomActionItem(MenuItemConfig itemConfig) {
        return itemConfig.actions.stream().anyMatch(action -> action.equalsIgnoreCase("delete"))
                && !plugin.getCustomTagManager().isDeleteEnabled();
    }

    private ItemStack createCustomActionItem(MenuItemConfig config, CustomTag customTag) {
        ItemStack item = new ItemStack(config.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(TextComponentParser.parse(applyCustomPlaceholders(config.name, customTag)));
        if (config.lore != null && !config.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : config.lore) {
                String parsedLine = applyCustomPlaceholders(line, customTag);
                if (!parsedLine.isEmpty()) {
                    lore.add(TextComponentParser.parse(parsedLine));
                }
            }
            meta.lore(lore);
        }

        if (config.itemModel != null && !config.itemModel.isEmpty()) {
            setItemModelCompat(meta, applyCustomPlaceholders(config.itemModel, customTag));
        }
        if (config.tooltip != null && !config.tooltip.isEmpty()) {
            setTooltipStyleCompat(meta, applyCustomPlaceholders(config.tooltip, customTag));
        }
        if (config.glow) {
            setGlowCompat(meta);
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private String applyCustomPlaceholders(String text, CustomTag customTag) {
        if (text == null) {
            return "";
        }

        String displayPrefix = plugin.getCustomTagManager().getDisplayPrefix(customTag);
        String displaySuffix = plugin.getCustomTagManager().getDisplaySuffix(customTag);
        double refundAmount = getRefundAmount(customTag);
        return text
                .replace("%tag_name%", LegacyColorConverter.convertToMiniMessage(plugin.getCustomTagManager().getDisplayName()))
                .replace("%tag_id%", customTag.getId())
                .replace("%custom_tag_id%", customTag.getId())
                .replace("%tag_prefix%", LegacyColorConverter.convertToMiniMessage(displayPrefix))
                .replace("%tag_suffix%", LegacyColorConverter.convertToMiniMessage(displaySuffix))
                .replace("%tag_full%", LegacyColorConverter.convertToMiniMessage(displayPrefix + displaySuffix))
                .replace("%payment_provider%", customTag.getPaymentProvider())
                .replace("%purchase_price%", formatAmount(customTag.getPurchasePrice()))
                .replace("%refund_percent%", formatAmount(plugin.getCustomTagManager().getRefundPercent()))
                .replace("%refund_amount%", formatAmount(refundAmount))
                .replace("%delete_status%", plugin.getCustomTagManager().isDeleteEnabled() ? "<green>已开启" : "<red>已关闭");
    }

    private double getRefundAmount(CustomTag customTag) {
        if (!plugin.getCustomTagManager().isRefundEnabled()) {
            return 0.0;
        }
        return customTag.getPurchasePrice() * plugin.getCustomTagManager().getRefundPercent() / 100.0;
    }

    private String formatAmount(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private ActionMenuConfig defaultCustomTagDetailMenu() {
        Map<Character, MenuItemConfig> menuItems = new HashMap<>();
        menuItems.put('X', menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), List.of()));
        menuItems.put('P', menuItem(Material.NAME_TAG, "<gold><!i>%tag_name%", List.of(
                "<gray>ID: %tag_id%",
                "<gray>前缀: %tag_prefix%",
                "<gray>后缀: %tag_suffix%",
                "<gray>退款比例: %refund_percent%%"
        ), List.of()));
        menuItems.put('E', menuItem(Material.EMERALD, "<green><!i>佩戴/取消佩戴", List.of("<yellow>点击切换这个自定义称号"), List.of("equip")));
        menuItems.put('D', menuItem(Material.BARRIER, "<red><!i>删除自定义称号", List.of("<gray>只能删除自己的自定义称号", "<gray>退款金额: %refund_amount%", "<yellow>点击进入确认菜单"), List.of("delete")));
        menuItems.put('B', menuItem(Material.ARROW, "<yellow><!i>返回称号仓库", List.of("<gray>点击返回称号仓库"), List.of("back")));
        return new ActionMenuConfig("<gold><!i>自定义称号详情", 45,
                List.of("XXXXXXXXX", "XXXXPXXXX", "XXEXDXBXX", "XXXXXXXXX", "XXXXXXXXX"), menuItems);
    }

    private ActionMenuConfig defaultCustomTagDeleteMenu() {
        Map<Character, MenuItemConfig> menuItems = new HashMap<>();
        menuItems.put('X', menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), List.of()));
        menuItems.put('P', menuItem(Material.NAME_TAG, "<red><!i>确认删除 %tag_name%", List.of(
                "<gray>ID: %tag_id%",
                "<gray>删除后会从称号仓库移除",
                "<gray>退款金额: %refund_amount%"
        ), List.of()));
        menuItems.put('Y', menuItem(Material.LIME_CONCRETE, "<green><!i>确认删除", List.of("<yellow>点击后立即删除"), List.of("confirm-delete")));
        menuItems.put('N', menuItem(Material.RED_CONCRETE, "<red><!i>取消", List.of("<gray>返回自定义称号详情"), List.of("cancel-delete")));
        return new ActionMenuConfig("<red><!i>删除自定义称号", 27,
                List.of("XXXXXXXXX", "XXYXPXNXX", "XXXXXXXXX"), menuItems);
    }

    private MenuItemConfig menuItem(Material material, String name, List<String> lore, List<String> actions) {
        MenuItemConfig config = new MenuItemConfig();
        config.material = material;
        config.name = name;
        config.lore = lore;
        config.actions = actions;
        return config;
    }

    private MenuItemConfig loadMenuItemConfig(ConfigurationSection section) {
        MenuItemConfig config = new MenuItemConfig();
        config.material = Material.matchMaterial(section.getString("material", "STONE"));
        if (config.material == null) config.material = Material.STONE;
        config.name = section.getString("name", "");
        config.lore = section.getStringList("lore");
        config.itemModel = section.getString("item_model");
        config.tooltip = section.getString("tooltip-style", section.getString("tooltip"));
        config.glow = section.getBoolean("glow", false);
        config.actions = section.getStringList("actions");
        if (config.actions.isEmpty() && section.isString("action")) {
            config.actions = List.of(section.getString("action", ""));
        }
        return config;
    }

    private ActionMenuConfig loadActionMenu(String resourcePath, ActionMenuConfig fallback) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String title = config.getString("title", fallback.title);
        int size = normalizeMenuSize(config.getInt("size", fallback.size));
        List<String> layout = config.getStringList("layout");
        if (layout.isEmpty()) {
            layout = fallback.layout;
        }

        Map<Character, MenuItemConfig> menuItems = new HashMap<>(fallback.items);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                if (key.length() != 1) {
                    continue;
                }
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    menuItems.put(key.charAt(0), loadMenuItemConfig(itemSection));
                }
            }
        }

        return new ActionMenuConfig(title, size, layout, menuItems);
    }

    public void openTagSelectMenu(Player player) {
        openTagSelectMenu(player, 0);
    }

    public void openTagSelectMenu(Player player, int page) {
        if (plugin.getFutureMenus().open(player, "tag-select", page, "")) return;
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
        if (data != null && data.hasCustomTag() && plugin.getCustomTagManager().isEnabled()) {
            availableTags.addAll(data.getCustomTags().keySet());
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

        Inventory inventory = Bukkit.createInventory(null, menuSize, TextComponentParser.parse(title));

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
            if (plugin.getTagManager().isCustomTagId(tagId)) {
                CustomTag customTag = data.getCustomTag(tagId);
                if (customTag == null) {
                    tagIndex++;
                    continue;
                }
                boolean isSelected = tagId.equals(currentTag);
                MenuItemConfig template = isSelected ? selectedTagItem : unselectedTagItem;
                inventory.setItem(slot, createCustomTagItem(template, customTag, tagId, isSelected));
                tagIndex++;
                continue;
            }

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
        UUID uuid = player.getUniqueId();
        reopeningMenus.add(uuid);
        try {
            openMenus.put(uuid, new MenuSession("tag-select", page, null, inventory));
            player.openInventory(inventory);
        } finally {
            reopeningMenus.remove(uuid);
        }
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
            meta.displayName(TextComponentParser.parse(name));
        }

        // 设置lore
        if (config.lore != null && !config.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : config.lore) {
                if (player != null) {
                    line = line.replace("%player%", player.getName());
                }
                lore.add(TextComponentParser.parse(line));
            }
            meta.lore(lore);
        }

        // 设置item_model（1.21.4+）
        if (config.itemModel != null && !config.itemModel.isEmpty()) {
            setItemModelCompat(meta, config.itemModel);
        }

        // 设置tooltip（1.21.4+）
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
                .replace("%tag_name%", LegacyColorConverter.convertToMiniMessage(tag.getDisplayName()))
                .replace("%tag_id%", tag.getId());
        meta.displayName(TextComponentParser.parse(name));

        // 设置lore
        if (template.lore != null && !template.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            long expireTime = data != null ? data.getTagExpireTime(tag.getId()) : -1;
            String expireStr = plugin.getMessageManager().formatExpireTime(expireTime);

            for (String line : template.lore) {
                line = line.replace("%tag_name%", LegacyColorConverter.convertToMiniMessage(tag.getDisplayName()))
                        .replace("%tag_id%", tag.getId())
                        .replace("%tag_prefix%", LegacyColorConverter.convertToMiniMessage(tag.getPrefix()))
                        .replace("%tag_suffix%", LegacyColorConverter.convertToMiniMessage(tag.getSuffix()))
                        .replace("%tag_expire%", expireStr);
                lore.add(TextComponentParser.parse(line));
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
            GuiItemMetaCompat.setCustomModelData(meta, tag.getCustomModelData());
        }

        String tooltipStyle = tag.getTooltipStyle();
        if (tooltipStyle == null || tooltipStyle.isEmpty()) {
            tooltipStyle = template.tooltip;
        }
        // 设置tooltip（1.21.4+）
        if (tooltipStyle != null && !tooltipStyle.isEmpty()) {
            setTooltipStyleCompat(meta, tooltipStyle);
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

    private ItemStack createCustomTagItem(MenuItemConfig template, CustomTag customTag, String tagId, boolean isSelected) {
        CustomTagManager.CustomTagIcon icon = plugin.getCustomTagManager().getIcon(customTag.getIconId());
        Material material = icon != null ? icon.material() : template.material;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String tagName = plugin.getCustomTagManager().getDisplayName();
        String name = template.name
                .replace("%tag_name%", LegacyColorConverter.convertToMiniMessage(tagName))
                .replace("%tag_id%", tagId);
        meta.displayName(TextComponentParser.parse(name));

        if (template.lore != null && !template.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            String displayPrefix = plugin.getCustomTagManager().getDisplayPrefix(customTag);
            String displaySuffix = plugin.getCustomTagManager().getDisplaySuffix(customTag);
            for (String line : template.lore) {
                line = line.replace("%tag_name%", LegacyColorConverter.convertToMiniMessage(tagName))
                        .replace("%tag_id%", tagId)
                        .replace("%tag_prefix%", LegacyColorConverter.convertToMiniMessage(displayPrefix))
                        .replace("%tag_suffix%", LegacyColorConverter.convertToMiniMessage(displaySuffix))
                        .replace("%tag_expire%", plugin.getMessageManager().formatExpireTime(-1));
                lore.add(TextComponentParser.parse(line));
            }
            meta.lore(lore);
        }

        if (icon != null) {
            if (icon.customModelData() > 0) {
                GuiItemMetaCompat.setCustomModelData(meta, icon.customModelData());
            }
            if (icon.itemModel() != null && !icon.itemModel().isEmpty()) {
                setItemModelCompat(meta, icon.itemModel());
            }
            if (icon.tooltipStyle() != null && !icon.tooltipStyle().isEmpty()) {
                setTooltipStyleCompat(meta, icon.tooltipStyle());
            }
        }

        if (template.glow || isSelected) {
            setGlowCompat(meta);
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return setTagId(item, tagId);
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
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (session.menuType.equals("custom-detail") || session.menuType.equals("custom-delete")) {
            handleCustomActionMenuClick(player, session, slot);
            return;
        }

        if (slot >= menuSize) return;

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

        if (plugin.getTagManager().isCustomTagId(tagId)) {
            if (!plugin.getTagManager().hasCustomTag(player.getUniqueId(), tagId)) {
                plugin.getMessageManager().send(player, "custom-tag-not-purchased");
                return;
            }
            openCustomTagDetailMenu(player, tagId, currentPage);
            return;
        }

        if (tagId.equals(currentTag)) {
            // 取消选择
            plugin.getTagManager().setCurrentTag(player.getUniqueId(), null);
            plugin.getMessageManager().send(player, "your-tag-cleared");
        } else {
            // 选择称号
            plugin.getTagManager().setCurrentTag(player.getUniqueId(), tagId);
            Tag tag = plugin.getTagManager().getTag(tagId);
            String tagName = plugin.getTagManager().isCustomTagId(tagId)
                    ? plugin.getCustomTagManager().getDisplayName()
                    : (tag != null ? tag.getDisplayName() : tagId);
            plugin.getMessageManager().send(player, "your-tag-set",
                    Map.of("tag", tagName));
        }

        // 播放音效
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // 直接刷新当前页物品，不关闭GUI
        refreshTagItems(player, currentPage);
    }

    private void handleCustomActionMenuClick(Player player, MenuSession session, int slot) {
        ActionMenuConfig menu = session.menuType.equals("custom-delete") ? customTagDeleteMenu : customTagDetailMenu;
        if (slot >= menu.size) {
            return;
        }

        int row = slot / 9;
        int col = slot % 9;
        if (row >= menu.layout.size() || col >= menu.layout.get(row).length()) {
            return;
        }

        char key = menu.layout.get(row).charAt(col);
        MenuItemConfig itemConfig = menu.items.get(key);
        if (itemConfig == null || itemConfig.actions == null || itemConfig.actions.isEmpty()) {
            return;
        }

        for (String action : itemConfig.actions) {
            handleCustomAction(player, session, action);
        }
    }

    private void handleCustomAction(Player player, MenuSession session, String action) {
        if (action == null || action.isBlank()) {
            return;
        }

        switch (action.toLowerCase(Locale.ROOT)) {
            case "equip" -> toggleCustomTag(player, session.customTagId, session.page);
            case "delete" -> openCustomTagDeleteMenu(player, session.customTagId, session.page);
            case "confirm-delete" -> plugin.getCustomTagManager().deleteOwnedCustomTag(
                    player,
                    session.customTagId,
                    () -> openTagSelectMenu(player, session.page),
                    () -> openCustomTagDetailMenu(player, session.customTagId, session.page)
            );
            case "cancel-delete" -> openCustomTagDetailMenu(player, session.customTagId, session.page);
            case "back" -> openTagSelectMenu(player, session.page);
            case "close" -> player.closeInventory();
            default -> {
            }
        }
    }

    private void toggleCustomTag(Player player, String customTagId, int currentPage) {
        if (!plugin.getTagManager().hasCustomTag(player.getUniqueId(), customTagId)) {
            plugin.getMessageManager().send(player, "custom-tag-not-purchased");
            openTagSelectMenu(player, currentPage);
            return;
        }

        PlayerTagData data = plugin.getTagManager().getPlayerData(player.getUniqueId());
        String currentTag = data != null ? data.getCurrentTag() : null;
        if (customTagId.equals(currentTag)) {
            plugin.getTagManager().setCurrentTag(player.getUniqueId(), null);
            plugin.getMessageManager().send(player, "your-tag-cleared");
        } else {
            plugin.getTagManager().setCurrentTag(player.getUniqueId(), customTagId);
            plugin.getMessageManager().send(player, "your-tag-set",
                    Map.of("tag", plugin.getCustomTagManager().getDisplayName()));
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        openCustomTagDetailMenu(player, customTagId, currentPage);
    }

    private void refreshTagItems(Player player, int page) {
        MenuSession session = openMenus.get(player.getUniqueId());
        if (session == null) return;
        Inventory inventory = session.inventory;

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
        if (data != null && data.hasCustomTag() && plugin.getCustomTagManager().isEnabled()) {
            availableTags.addAll(data.getCustomTags().keySet());
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
            if (plugin.getTagManager().isCustomTagId(tagId)) {
                CustomTag customTag = data.getCustomTag(tagId);
                if (customTag == null) {
                    tagIndex++;
                    continue;
                }
                boolean isSelected = tagId.equals(currentTag);
                MenuItemConfig template = isSelected ? selectedTagItem : unselectedTagItem;
                inventory.setItem(slot, createCustomTagItem(template, customTag, tagId, isSelected));
                tagIndex++;
                continue;
            }

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
                    org.bukkit.Sound soundEnum = gg.fotia.tags.util.SoundResolver.resolve(parts[0]);
                    float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    if (soundEnum != null) {
                        player.playSound(player.getLocation(), soundEnum, volume, pitch);
                    }
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
                player.sendMessage(TextComponentParser.parse(message));
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            UUID uuid = player.getUniqueId();
            if (reopeningMenus.contains(uuid)) {
                return;
            }
            openMenus.remove(uuid);
        }
    }

    // 兼容1.20.4的方法 - setItemModel在1.21.4+才有
    private void setItemModelCompat(ItemMeta meta, String itemModel) {
        GuiItemMetaCompat.setItemModel(meta, itemModel);
    }

    // 兼容1.20.4的方法 - setTooltipStyle在1.21.4+才有
    private void setTooltipStyleCompat(ItemMeta meta, String tooltip) {
        GuiItemMetaCompat.setTooltipStyle(meta, tooltip);
    }

    // 兼容1.20.4的方法 - setEnchantmentGlintOverride在1.21.4+才有
    private void setGlowCompat(ItemMeta meta) {
        GuiItemMetaCompat.setGlow(meta);
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
        String customTagId;
        final Inventory inventory;

        MenuSession(String menuType, int page, String customTagId, Inventory inventory) {
            this.menuType = menuType;
            this.page = page;
            this.customTagId = customTagId;
            this.inventory = inventory;
        }
    }

    private static class ActionMenuConfig {
        String title;
        int size;
        List<String> layout;
        Map<Character, MenuItemConfig> items;

        ActionMenuConfig(String title, int size, List<String> layout, Map<Character, MenuItemConfig> items) {
            this.title = title;
            this.size = size;
            this.layout = layout;
            this.items = items;
        }
    }
}
