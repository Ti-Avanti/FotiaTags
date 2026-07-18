package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.hook.PaymentManager;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.TagManager;
import gg.fotia.tags.util.CustomTagTextFilter;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.PlayerOperationLock;
import gg.fotia.tags.util.SafeMiniMessageValidator;
import gg.fotia.tags.util.TextComponentParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class CustomTagManager implements Listener {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)[&§]#[0-9a-f]{6}");
    private static final Pattern BUKKIT_HEX_COLOR_PATTERN = Pattern.compile("(?i)[&§]x([&§][0-9a-f]){6}");
    private static final Pattern BASIC_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9a-fk-or]");
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile("<[^>]+>");

    private final FotiaTags plugin;
    private final Map<UUID, CustomTagDraft> drafts = new HashMap<>();
    private final Map<UUID, InputType> inputSessions = new HashMap<>();
    private final Map<UUID, MenuType> openMenus = new HashMap<>();
    private final Set<UUID> reopeningMenus = new HashSet<>();
    private final Map<String, CustomTagIcon> icons = new LinkedHashMap<>();
    private final PlayerOperationLock purchaseOperations = new PlayerOperationLock();
    private final PlayerOperationLock deleteOperations = new PlayerOperationLock();

    private boolean enabled;
    private String displayName;
    private int maxPrefixLength;
    private int maxSuffixLength;
    private boolean allowColors;
    private boolean allowMiniMessage;
    private boolean prefixEditable;
    private boolean suffixEditable;
    private boolean displayWrapperEnabled;
    private String displayWrapperLeft;
    private String displayWrapperRight;
    private int maxOwned;
    private boolean deleteEnabled;
    private boolean refundEnabled;
    private double refundPercent;
    private String defaultIconId;
    private CustomTagTextFilter textFilter;
    private CustomMenu editMenu;
    private CustomMenu iconMenu;
    private CustomMenuItem iconItemTemplate;
    private CustomMenuTexts menuTexts;

    public CustomTagManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        var config = plugin.getConfigManager().getConfig();
        this.enabled = config.getBoolean("custom-tags.enabled", false);
        this.displayName = config.getString("custom-tags.display-name", "自定义称号");
        this.maxPrefixLength = Math.max(1, config.getInt("custom-tags.limits.max-prefix-length", 16));
        this.maxSuffixLength = Math.max(1, config.getInt("custom-tags.limits.max-suffix-length", 16));
        this.allowColors = config.getBoolean("custom-tags.limits.allow-colors", true);
        this.allowMiniMessage = config.getBoolean("custom-tags.limits.allow-minimessage", false);
        this.prefixEditable = config.getBoolean("custom-tags.editable-parts.prefix", true);
        this.suffixEditable = config.getBoolean("custom-tags.editable-parts.suffix", true);
        if (!prefixEditable && !suffixEditable) {
            plugin.getLogger().warning("custom-tags.editable-parts cannot disable both prefix and suffix; prefix has been enabled as fallback.");
            this.prefixEditable = true;
        }
        this.displayWrapperEnabled = config.getBoolean("custom-tags.display-wrapper.enabled", true);
        this.displayWrapperLeft = config.getString("custom-tags.display-wrapper.left", "[");
        this.displayWrapperRight = config.getString("custom-tags.display-wrapper.right", "]");
        this.maxOwned = config.getInt("custom-tags.max-owned", -1);
        this.deleteEnabled = config.getBoolean("custom-tags.delete.enabled", true);
        this.refundEnabled = config.getBoolean("custom-tags.delete.refund.enabled", false);
        this.refundPercent = clamp(config.getDouble("custom-tags.delete.refund.percent", 0.0), 0.0, 100.0);
        this.defaultIconId = config.getString("custom-tags.content.default-icon",
                config.getString("custom-tags.default-icon", "nametag"));
        this.textFilter = new CustomTagTextFilter(
                config.getBoolean("custom-tags.filter.enabled", false),
                config.getBoolean("custom-tags.filter.ignore-case", true),
                config.getBoolean("custom-tags.filter.strip-colors-before-check", true),
                config.getBoolean("custom-tags.filter.strip-minimessage-before-check", true),
                config.getStringList("custom-tags.filter.blocked-words"),
                config.getStringList("custom-tags.filter.blocked-regex")
        );

        loadIcons(config.getConfigurationSection("custom-tags.icons"));
        loadMenuConfigs();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CustomTagIcon getIcon(String iconId) {
        return icons.get(iconId);
    }

    public boolean isDeleteEnabled() {
        return deleteEnabled;
    }

    public boolean isRefundEnabled() {
        return refundEnabled;
    }

    public double getRefundPercent() {
        return refundPercent;
    }

    public String getDisplayPrefix(CustomTag customTag) {
        if (customTag == null) {
            return "";
        }
        if (!displayWrapperEnabled) {
            return customTag.getPrefix();
        }

        String prefix = safeText(customTag.getPrefix());
        if (prefix.isEmpty()) {
            return "";
        }
        return safeText(displayWrapperLeft) + prefix + safeText(displayWrapperRight);
    }

    public String getDisplaySuffix(CustomTag customTag) {
        if (customTag == null) {
            return "";
        }
        if (!displayWrapperEnabled) {
            return customTag.getSuffix();
        }

        String suffix = safeText(customTag.getSuffix());
        if (suffix.isEmpty()) {
            return "";
        }
        return safeText(displayWrapperLeft) + suffix + safeText(displayWrapperRight);
    }

    private String safeText(String text) {
        return text != null ? text : "";
    }

    public void openCustomMenu(Player player) {
        if (!enabled) {
            plugin.getMessageManager().send(player, "custom-tag-disabled");
            return;
        }

        CustomTagDraft draft = getDraft(player);
        boolean purchased = hasReachedMaxOwned(player);
        Inventory inventory = Bukkit.createInventory(null, editMenu.size(), parse(editMenu.title()));

        renderLayout(inventory, editMenu, player, draft, purchased, false);
        openManagedInventory(player, inventory, MenuType.EDIT);
    }

    public void openIconMenu(Player player) {
        if (!enabled) {
            plugin.getMessageManager().send(player, "custom-tag-disabled");
            return;
        }

        CustomTagDraft draft = getDraft(player);
        boolean purchased = hasReachedMaxOwned(player);
        Inventory inventory = Bukkit.createInventory(null, iconMenu.size(), parse(iconMenu.title()));

        renderLayout(inventory, iconMenu, player, draft, purchased, true);
        int index = 0;
        for (CustomTagIcon icon : icons.values()) {
            if (index >= iconMenu.iconSlots().size()) {
                break;
            }

            int slot = iconMenu.iconSlots().get(index);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, createIconSelectItem(icon));
            }
            index++;
        }

        openManagedInventory(player, inventory, MenuType.ICON);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MenuType menuType = openMenus.get(player.getUniqueId());
        if (menuType == null) {
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

        if (menuType == MenuType.ICON) {
            String iconId = getString(item, "custom_icon");
            if (iconId != null) {
                if (hasReachedMaxOwned(player)) {
                    sendLocked(player);
                    openCustomMenu(player);
                    return;
                }

                CustomTagDraft draft = getDraft(player);
                drafts.put(player.getUniqueId(), draft.withIconId(iconId));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openCustomMenu(player);
                return;
            }
        }

        String action = getString(item, "custom_action");
        if (action == null) {
            return;
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        if (hasReachedMaxOwned(player) && isLockedAction(action)) {
            sendLocked(player);
            openCustomMenu(player);
            return;
        }

        switch (action) {
            case "prefix" -> beginInput(player, InputType.PREFIX);
            case "suffix" -> beginInput(player, InputType.SUFFIX);
            case "icon" -> openIconMenu(player);
            case "confirm" -> handleConfirm(player);
            case "back" -> {
                openMenus.remove(player.getUniqueId());
                plugin.getMenuManager().openTagSelectMenu(player);
            }
            case "back-edit" -> openCustomMenu(player);
            default -> {
            }
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        InputType inputType = inputSessions.remove(uuid);
        if (inputType == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(event.getPlayer(), inputType, input));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyAsyncChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        InputType inputType = inputSessions.remove(uuid);
        if (inputType == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(event.getPlayer(), inputType, input));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        drafts.remove(uuid);
        inputSessions.remove(uuid);
        openMenus.remove(uuid);
    }

    private void loadIcons(ConfigurationSection iconsSection) {
        icons.clear();
        if (iconsSection == null) {
            return;
        }

        for (String iconId : iconsSection.getKeys(false)) {
            ConfigurationSection section = iconsSection.getConfigurationSection(iconId);
            if (section == null) {
                continue;
            }

            Material material = Material.matchMaterial(section.getString("material", "NAME_TAG"));
            icons.put(iconId, new CustomTagIcon(
                    iconId,
                    section.getString("name", iconId),
                    material != null ? material : Material.NAME_TAG,
                    section.getInt("custom-model-data", 0),
                    section.getString("item-model", ""),
                    section.getString("tooltip-style", "")
            ));
        }
    }

    private void loadMenuConfigs() {
        this.editMenu = loadMenu("menus/custom-tag.yml", defaultEditMenu());
        this.iconMenu = loadMenu("menus/custom-tag-icons.yml", defaultIconMenu());

        File editMenuFile = new File(plugin.getDataFolder(), "menus/custom-tag.yml");
        YamlConfiguration editConfig = YamlConfiguration.loadConfiguration(editMenuFile);
        this.menuTexts = loadMenuTexts(editConfig);

        File iconMenuFile = new File(plugin.getDataFolder(), "menus/custom-tag-icons.yml");
        YamlConfiguration iconConfig = YamlConfiguration.loadConfiguration(iconMenuFile);
        ConfigurationSection iconItemSection = iconConfig.getConfigurationSection("icon-item");
        this.iconItemTemplate = iconItemSection != null ? loadMenuItem(iconItemSection) : defaultIconItem();
    }

    private CustomMenuTexts loadMenuTexts(YamlConfiguration config) {
        String path = "placeholder-texts.";
        CustomMenuTexts defaults = defaultMenuTexts();
        return new CustomMenuTexts(
                config.getString(path + "not-set", defaults.notSet()),
                config.getString(path + "status-purchased", defaults.statusPurchased()),
                config.getString(path + "status-not-purchased", defaults.statusNotPurchased()),
                config.getString(path + "complete", defaults.complete()),
                config.getString(path + "incomplete", defaults.incomplete()),
                config.getString(path + "confirm-save-name", defaults.confirmSaveName()),
                config.getString(path + "confirm-buy-name", defaults.confirmBuyName()),
                config.getString(path + "confirm-save-lore", defaults.confirmSaveLore()),
                config.getString(path + "confirm-buy-lore", defaults.confirmBuyLore()),
                config.getString(path + "confirm-locked-name", defaults.confirmLockedName()),
                config.getString(path + "confirm-locked-lore", defaults.confirmLockedLore()),
                config.getString(path + "confirm-incomplete-lore", defaults.confirmIncompleteLore()),
                config.getString(path + "confirm-click-lore", defaults.confirmClickLore()),
                config.getString(path + "part-enabled", defaults.partEnabled()),
                config.getString(path + "part-disabled", defaults.partDisabled())
        );
    }

    private CustomMenu loadMenu(String resourcePath, CustomMenu fallback) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String title = config.getString("title", fallback.title());
        int size = normalizeMenuSize(config.getInt("size", fallback.size()));
        List<String> layout = config.getStringList("layout");
        if (layout.isEmpty()) {
            layout = fallback.layout();
        }

        Map<Character, CustomMenuItem> items = new HashMap<>(fallback.items());
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                if (key.length() != 1) {
                    continue;
                }
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    items.put(key.charAt(0), loadMenuItem(itemSection));
                }
            }
        }

        List<Integer> iconSlots = cleanSlots(config.getIntegerList("icon-slots"), size);
        if (iconSlots.isEmpty()) {
            iconSlots = fallback.iconSlots();
        }

        return new CustomMenu(title, size, layout, items, iconSlots);
    }

    private List<Integer> cleanSlots(List<Integer> slots, int menuSize) {
        List<Integer> validSlots = new ArrayList<>();
        for (int slot : slots) {
            if (slot >= 0 && slot < menuSize) {
                validSlots.add(slot);
            }
        }
        return validSlots;
    }

    private CustomMenuItem loadMenuItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }

        return new CustomMenuItem(
                material,
                section.getString("name", ""),
                section.getStringList("lore"),
                section.getString("action", ""),
                section.getString("item-model", section.getString("item_model", "")),
                section.getString("tooltip-style", section.getString("tooltip", "")),
                section.getBoolean("glow", false),
                section.getBoolean("use-selected-icon", false)
        );
    }

    private void renderLayout(Inventory inventory, CustomMenu menu, Player player, CustomTagDraft draft, boolean purchased, boolean iconMenuLayout) {
        for (int row = 0; row < menu.layout().size() && row < menu.size() / 9; row++) {
            String rowLayout = menu.layout().get(row);
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                char key = rowLayout.charAt(col);
                if (key == ' ' || (iconMenuLayout && key == 'X')) {
                    continue;
                }

                CustomMenuItem itemConfig = menu.items().get(key);
                if (itemConfig == null) {
                    continue;
                }

                int slot = row * 9 + col;
                inventory.setItem(slot, createConfiguredItem(itemConfig, player, draft, purchased));
            }
        }
    }

    private ItemStack createConfiguredItem(CustomMenuItem config, Player player, CustomTagDraft draft, boolean purchased) {
        CustomTagIcon selectedIcon = icons.get(draft.iconId());
        Material material = config.useSelectedIcon() && selectedIcon != null ? selectedIcon.material() : config.material();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(parse(applyPlaceholders(config.name(), player, draft, purchased, selectedIcon, null)));

        if (!config.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : config.lore()) {
                String parsedLine = applyPlaceholders(line, player, draft, purchased, selectedIcon, null);
                if (!parsedLine.isEmpty()) {
                    lore.add(parse(parsedLine));
                }
            }
            meta.lore(lore);
        }

        if (config.useSelectedIcon()) {
            applyIconMeta(meta, selectedIcon);
        } else {
            applyItemMeta(meta, config);
        }

        if (config.glow()) {
            setGlowCompat(meta);
        }

        if (config.action() != null && !config.action().isEmpty() && !config.action().equalsIgnoreCase("preview")) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_action"), PersistentDataType.STRING, config.action());
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createIconSelectItem(CustomTagIcon icon) {
        ItemStack item = new ItemStack(icon.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(parse(applyPlaceholders(iconItemTemplate.name(), null, null, false, null, icon)));
        if (!iconItemTemplate.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : iconItemTemplate.lore()) {
                lore.add(parse(applyPlaceholders(line, null, null, false, null, icon)));
            }
            meta.lore(lore);
        }

        applyIconMeta(meta, icon);
        if (iconItemTemplate.glow()) {
            setGlowCompat(meta);
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_icon"), PersistentDataType.STRING, icon.id());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private String applyPlaceholders(String text, Player player, CustomTagDraft draft, boolean purchased, CustomTagIcon selectedIcon, CustomTagIcon icon) {
        if (text == null) {
            return "";
        }

        String prefix = draft != null ? draft.prefix() : "";
        String suffix = draft != null ? draft.suffix() : "";
        String displayPrefix = getDraftDisplayPrefix(prefix);
        String displaySuffix = getDraftDisplaySuffix(suffix);
        String displayFull = displayPrefix + displaySuffix;
        String iconName = selectedIcon != null ? selectedIcon.name() : "";
        boolean complete = draft != null && draft.isComplete(prefixEditable, suffixEditable) && icons.containsKey(draft.iconId());
        String priceText = plugin.getPaymentManager().getPriceText();
        String confirmName = purchased ? menuTexts.confirmLockedName() : menuTexts.confirmBuyName();
        String confirmLore1 = purchased
                ? menuTexts.confirmLockedLore()
                : (complete ? menuTexts.confirmBuyLore().replace("%price%", priceText) : menuTexts.confirmIncompleteLore());
        String confirmLore2 = !purchased && complete ? menuTexts.confirmClickLore() : "";

        return text
                .replace("%player%", player != null ? player.getName() : "")
                .replace("%display_name%", LegacyColorConverter.convertToMiniMessage(displayName))
                .replace("%prefix%", LegacyColorConverter.convertToMiniMessage(prefix))
                .replace("%suffix%", LegacyColorConverter.convertToMiniMessage(suffix))
                .replace("%prefix_or_none%", emptyText(prefix))
                .replace("%suffix_or_none%", emptyText(suffix))
                .replace("%display_prefix%", LegacyColorConverter.convertToMiniMessage(displayPrefix))
                .replace("%display_suffix%", LegacyColorConverter.convertToMiniMessage(displaySuffix))
                .replace("%display_full%", LegacyColorConverter.convertToMiniMessage(displayFull))
                .replace("%display_prefix_or_none%", emptyText(displayPrefix))
                .replace("%display_suffix_or_none%", emptyText(displaySuffix))
                .replace("%display_full_or_none%", emptyText(displayFull))
                .replace("%prefix_status%", partStatus(prefixEditable))
                .replace("%suffix_status%", partStatus(suffixEditable))
                .replace("%icon%", LegacyColorConverter.convertToMiniMessage(iconName))
                .replace("%icon_or_none%", emptyText(iconName))
                .replace("%status%", purchased ? menuTexts.statusPurchased() : menuTexts.statusNotPurchased())
                .replace("%price%", priceText)
                .replace("%complete%", complete ? menuTexts.complete() : menuTexts.incomplete())
                .replace("%confirm_name%", confirmName)
                .replace("%confirm_lore_1%", confirmLore1)
                .replace("%confirm_lore_2%", confirmLore2)
                .replace("%icon_id%", icon != null ? icon.id() : "")
                .replace("%icon_name%", icon != null ? LegacyColorConverter.convertToMiniMessage(icon.name()) : "");
    }

    private void beginInput(Player player, InputType inputType) {
        if (!isPartEditable(inputType)) {
            plugin.getMessageManager().send(player, "custom-tag-part-disabled");
            openCustomMenu(player);
            return;
        }

        if (hasReachedMaxOwned(player)) {
            sendLocked(player);
            openCustomMenu(player);
            return;
        }

        inputSessions.put(player.getUniqueId(), inputType);
        openMenus.remove(player.getUniqueId());
        player.closeInventory();
        plugin.getMessageManager().send(player, inputType == InputType.PREFIX ? "custom-tag-input-prefix" : "custom-tag-input-suffix");
    }

    private void openManagedInventory(Player player, Inventory inventory, MenuType menuType) {
        UUID uuid = player.getUniqueId();
        reopeningMenus.add(uuid);
        try {
            openMenus.put(uuid, menuType);
            player.openInventory(inventory);
        } finally {
            reopeningMenus.remove(uuid);
        }
    }

    private boolean hasReachedMaxOwned(Player player) {
        if (maxOwned < 0) {
            return false;
        }
        return plugin.getTagManager().getCustomTags(player.getUniqueId()).size() >= maxOwned;
    }

    private boolean isLockedAction(String action) {
        return action != null && switch (action) {
            case "prefix", "suffix", "icon", "confirm" -> true;
            default -> false;
        };
    }

    private void sendLocked(Player player) {
        plugin.getMessageManager().send(player, "custom-tag-limit-reached",
                MessageManager.of("max", maxOwned < 0 ? "unlimited" : String.valueOf(maxOwned)));
    }

    private void handleInput(Player player, InputType inputType, String input) {
        if (!isPartEditable(inputType)) {
            plugin.getMessageManager().send(player, "custom-tag-part-disabled");
            openCustomMenu(player);
            return;
        }

        if (hasReachedMaxOwned(player)) {
            plugin.getMessageManager().send(player, "custom-tag-input-cancelled");
            sendLocked(player);
            openCustomMenu(player);
            return;
        }

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("取消")) {
            plugin.getMessageManager().send(player, "custom-tag-input-cancelled");
            openCustomMenu(player);
            return;
        }

        int maxLength = inputType == InputType.PREFIX ? maxPrefixLength : maxSuffixLength;
        if (!validateText(player, input, maxLength)) {
            openCustomMenu(player);
            return;
        }

        CustomTagDraft draft = getDraft(player);
        if (inputType == InputType.PREFIX) {
            draft = draft.withPrefix(input);
        } else {
            draft = draft.withSuffix(input);
        }
        drafts.put(player.getUniqueId(), draft);
        openCustomMenu(player);
    }

    private boolean validateText(Player player, String input, int maxLength) {
        if (input.isEmpty()) {
            plugin.getMessageManager().send(player, "custom-tag-empty");
            return false;
        }
        if (visibleLength(input) > maxLength) {
            plugin.getMessageManager().send(player, "custom-tag-too-long", MessageManager.of("max", String.valueOf(maxLength)));
            return false;
        }
        if (!allowColors && containsLegacyColor(input)) {
            plugin.getMessageManager().send(player, "custom-tag-colors-denied");
            return false;
        }
        if ((!allowMiniMessage && MINIMESSAGE_PATTERN.matcher(input).find())
                || (allowMiniMessage && !SafeMiniMessageValidator.isSafe(input))) {
            plugin.getMessageManager().send(player, "custom-tag-minimessage-denied");
            return false;
        }
        if (textFilter.findViolation(input).isPresent()) {
            plugin.getMessageManager().send(player, "custom-tag-blocked-word");
            return false;
        }
        return true;
    }

    private int visibleLength(String input) {
        String stripped = stripLegacyColors(input);
        stripped = MINIMESSAGE_PATTERN.matcher(stripped).replaceAll("");
        return stripped.length();
    }

    private boolean containsLegacyColor(String input) {
        return HEX_COLOR_PATTERN.matcher(input).find()
                || BUKKIT_HEX_COLOR_PATTERN.matcher(input).find()
                || BASIC_COLOR_PATTERN.matcher(input).find();
    }

    private String stripLegacyColors(String input) {
        String stripped = BUKKIT_HEX_COLOR_PATTERN.matcher(input).replaceAll("");
        stripped = HEX_COLOR_PATTERN.matcher(stripped).replaceAll("");
        return BASIC_COLOR_PATTERN.matcher(stripped).replaceAll("");
    }

    private void handleConfirm(Player player) {
        CustomTagDraft draft = getDraft(player);
        if (!draft.isComplete(prefixEditable, suffixEditable) || !icons.containsKey(draft.iconId())) {
            plugin.getMessageManager().send(player, "custom-tag-incomplete");
            openCustomMenu(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!purchaseOperations.tryAcquire(playerId)) {
            plugin.getMessageManager().send(player, "custom-tag-purchase-processing");
            return;
        }

        if (hasReachedMaxOwned(player)) {
            purchaseOperations.release(playerId);
            sendLocked(player);
            openCustomMenu(player);
            return;
        }

        PaymentManager.PaymentSnapshot snapshot = plugin.getPaymentManager().createSnapshot();
        String customTagId = plugin.getTagManager().createCustomTagId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        CustomTag customTag = draft.toCustomTag(customTagId, prefixEditable, suffixEditable, snapshot.provider(), snapshot.amount());
        if (textFilter.findViolation(customTag.getPrefix() + customTag.getSuffix()).isPresent()) {
            purchaseOperations.release(playerId);
            plugin.getMessageManager().send(player, "custom-tag-blocked-word");
            openCustomMenu(player);
            return;
        }

        if (!plugin.getPaymentManager().isAvailable(snapshot)) {
            purchaseOperations.release(playerId);
            plugin.getMessageManager().send(player, "custom-tag-payment-unavailable");
            return;
        }
        if (!plugin.getPaymentManager().hasEnough(player, snapshot)) {
            purchaseOperations.release(playerId);
            plugin.getMessageManager().send(player, "custom-tag-insufficient-funds",
                    MessageManager.of("price", plugin.getPaymentManager().getPriceText(snapshot)));
            return;
        }
        if (!plugin.getPaymentManager().withdraw(player, snapshot)) {
            purchaseOperations.release(playerId);
            plugin.getMessageManager().send(player, "custom-tag-purchase-failed");
            return;
        }

        persistCustomTag(player, customTag);
    }

    private void persistCustomTag(Player player, CustomTag customTag) {
        UUID playerId = player.getUniqueId();
        try {
            plugin.getDatabaseManager().saveCustomTag(playerId, customTag)
                    .whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (throwable != null) {
                                boolean refunded = plugin.getPaymentManager().refund(
                                        player, customTag.getPaymentProvider(), customTag.getPurchasePrice());
                                plugin.getLogger().severe("Failed to save custom tag " + customTag.getId()
                                        + " for " + playerId + ": " + throwable.getMessage());
                                if (player.isOnline()) {
                                    plugin.getMessageManager().send(player, "custom-tag-purchase-failed");
                                    if (!refunded) {
                                        plugin.getMessageManager().send(player, "custom-tag-refund-failed");
                                    }
                                    openCustomMenu(player);
                                }
                                if (!refunded) {
                                    plugin.getLogger().severe("Failed to refund " + customTag.getPurchasePrice()
                                            + " via " + customTag.getPaymentProvider() + " to " + playerId);
                                }
                                return;
                            }

                            plugin.getTagManager().updateCustomTag(playerId, customTag);
                            drafts.remove(playerId);
                            if (player.isOnline()) {
                                plugin.getMessageManager().send(player, "custom-tag-created");
                                openCustomMenu(player);
                            }
                        } finally {
                            purchaseOperations.release(playerId);
                        }
                    }));
        } catch (RuntimeException exception) {
            purchaseOperations.release(playerId);
            boolean refunded = plugin.getPaymentManager().refund(
                    player, customTag.getPaymentProvider(), customTag.getPurchasePrice());
            plugin.getLogger().severe("Failed to start custom tag save for " + playerId + ": " + exception.getMessage());
            plugin.getMessageManager().send(player, "custom-tag-purchase-failed");
            if (!refunded) {
                plugin.getMessageManager().send(player, "custom-tag-refund-failed");
            }
        }
    }

    private CustomTagDraft getDraft(Player player) {
        UUID uuid = player.getUniqueId();
        CustomTagDraft draft = drafts.get(uuid);
        if (draft != null) {
            return draft;
        }

        draft = new CustomTagDraft("", "", resolveDefaultIconId());
        drafts.put(uuid, draft);
        return draft;
    }

    public void deleteOwnedCustomTag(Player player, String customTagId, Runnable afterSuccess, Runnable afterFailure) {
        if (!deleteEnabled) {
            plugin.getMessageManager().send(player, "custom-tag-delete-disabled");
            runCallback(afterFailure);
            return;
        }
        CustomTag customTag = plugin.getTagManager().getCustomTag(player.getUniqueId(), customTagId);
        if (customTag == null) {
            plugin.getMessageManager().send(player, "custom-tag-not-purchased");
            runCallback(afterFailure);
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!deleteOperations.tryAcquire(playerId)) {
            plugin.getMessageManager().send(player, "custom-tag-delete-processing");
            runCallback(afterFailure);
            return;
        }

        double refundAmount = calculateRefundAmount(customTag);
        try {
            plugin.getTagManager().removeCustomTagAsync(playerId, customTagId)
                    .whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (throwable != null) {
                                if (player.isOnline()) {
                                    plugin.getMessageManager().send(player, "custom-tag-delete-failed");
                                    runCallback(afterFailure);
                                }
                                return;
                            }

                            if (player.isOnline()) {
                                plugin.getMessageManager().send(player, "custom-tag-deleted");
                            }
                            if (refundAmount > 0.0) {
                                boolean refunded = plugin.getPaymentManager().refund(player, customTag.getPaymentProvider(), refundAmount);
                                if (player.isOnline()) {
                                    plugin.getMessageManager().send(player,
                                            refunded ? "custom-tag-refunded" : "custom-tag-refund-failed",
                                            MessageManager.of("amount", formatAmount(refundAmount), "provider", customTag.getPaymentProvider()));
                                }
                                if (!refunded) {
                                    plugin.getLogger().severe("Failed to refund deleted custom tag " + customTagId
                                            + " to " + playerId);
                                }
                            }
                            if (player.isOnline()) {
                                runCallback(afterSuccess);
                            }
                        } finally {
                            deleteOperations.release(playerId);
                        }
                    }));
        } catch (RuntimeException exception) {
            deleteOperations.release(playerId);
            plugin.getLogger().severe("Failed to start custom tag deletion for " + playerId + ": " + exception.getMessage());
            plugin.getMessageManager().send(player, "custom-tag-delete-failed");
            runCallback(afterFailure);
        }
    }

    private double calculateRefundAmount(CustomTag customTag) {
        if (!refundEnabled || refundPercent <= 0.0 || customTag == null) {
            return 0.0;
        }
        return customTag.getPurchasePrice() * refundPercent / 100.0;
    }

    private String formatAmount(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
    }

    private void runCallback(Runnable callback) {
        if (callback != null) {
            callback.run();
        }
    }

    private String resolveDefaultIconId() {
        if (icons.containsKey(defaultIconId)) {
            return defaultIconId;
        }
        return icons.keySet().stream().findFirst().orElse("");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void applyItemMeta(ItemMeta meta, CustomMenuItem item) {
        if (item.itemModel() != null && !item.itemModel().isEmpty()) {
            setItemModelCompat(meta, item.itemModel());
        }
        if (item.tooltipStyle() != null && !item.tooltipStyle().isEmpty()) {
            setTooltipStyleCompat(meta, item.tooltipStyle());
        }
    }

    private void applyIconMeta(ItemMeta meta, CustomTagIcon icon) {
        if (icon == null) {
            return;
        }
        if (icon.customModelData() > 0) {
            meta.setCustomModelData(icon.customModelData());
        }
        if (icon.itemModel() != null && !icon.itemModel().isEmpty()) {
            setItemModelCompat(meta, icon.itemModel());
        }
        if (icon.tooltipStyle() != null && !icon.tooltipStyle().isEmpty()) {
            setTooltipStyleCompat(meta, icon.tooltipStyle());
        }
    }

    private String getString(ItemStack item, String key) {
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

    private Component parse(String text) {
        return TextComponentParser.parse(text);
    }

    private String emptyText(String text) {
        return text == null || text.isEmpty() ? menuTexts.notSet() : LegacyColorConverter.convertToMiniMessage(text);
    }

    private String getDraftDisplayPrefix(String prefix) {
        return getDraftDisplayPart(prefix);
    }

    private String getDraftDisplaySuffix(String suffix) {
        return getDraftDisplayPart(suffix);
    }

    private String getDraftDisplayPart(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!displayWrapperEnabled) {
            return text;
        }
        return safeText(displayWrapperLeft) + text + safeText(displayWrapperRight);
    }

    private String partStatus(boolean editable) {
        return editable ? menuTexts.partEnabled() : menuTexts.partDisabled();
    }

    private boolean isPartEditable(InputType inputType) {
        return inputType == InputType.PREFIX ? prefixEditable : suffixEditable;
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

    private void setItemModelCompat(ItemMeta meta, String itemModel) {
        GuiItemMetaCompat.setItemModel(meta, itemModel);
    }

    private void setTooltipStyleCompat(ItemMeta meta, String tooltipStyle) {
        GuiItemMetaCompat.setTooltipStyle(meta, tooltipStyle);
    }

    private void setGlowCompat(ItemMeta meta) {
        GuiItemMetaCompat.setGlow(meta);
    }

    private CustomMenu defaultEditMenu() {
        Map<Character, CustomMenuItem> items = new HashMap<>();
        items.put('X', new CustomMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", "", "", false, false));
        items.put('P', new CustomMenuItem(Material.NAME_TAG, "<!i>%prefix%%display_name%%suffix%",
                List.of("<gray>状态: %status%", "<gray>前缀: %prefix_or_none% <dark_gray>(%prefix_status%)", "<gray>后缀: %suffix_or_none% <dark_gray>(%suffix_status%)", "<gray>图标: %icon_or_none%"),
                "preview", "", "", false, true));
        items.put('F', new CustomMenuItem(Material.NAME_TAG, "<yellow><!i>设置前缀",
                List.of("<gray>状态: %prefix_status%", "<gray>当前: %prefix_or_none%", "<yellow>点击后在聊天栏输入"), "prefix", "", "", false, false));
        items.put('S', new CustomMenuItem(Material.PAPER, "<yellow><!i>设置后缀",
                List.of("<gray>状态: %suffix_status%", "<gray>当前: %suffix_or_none%", "<yellow>点击后在聊天栏输入"), "suffix", "", "", false, false));
        items.put('I', new CustomMenuItem(Material.CHEST, "<yellow><!i>选择图标",
                List.of("<gray>当前: %icon_or_none%", "<yellow>点击打开图标列表"), "icon", "", "", false, true));
        items.put('C', new CustomMenuItem(Material.EMERALD, "%confirm_name%",
                List.of("%confirm_lore_1%", "%confirm_lore_2%"), "confirm", "", "", false, false));
        items.put('B', new CustomMenuItem(Material.ARROW, "<yellow><!i>返回称号仓库",
                List.of("<gray>点击返回称号仓库"), "back", "", "", false, false));
        return new CustomMenu("<gold><!i>自定义称号", 45, List.of("XXXXXXXXX", "XXXXPXXXX", "XXFXSXIXX", "XXXXXXXXX", "BXXXCXXXX"), items, List.of());
    }

    private CustomMenu defaultIconMenu() {
        Map<Character, CustomMenuItem> items = new HashMap<>();
        items.put('D', new CustomMenuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", "", "", false, false));
        items.put('B', new CustomMenuItem(Material.ARROW, "<yellow><!i>返回", List.of("<gray>点击返回自定义称号菜单"), "back-edit", "", "", false, false));
        return new CustomMenu("<gold><!i>选择称号图标", 45,
                List.of("DDDDDDDDD", "DXXXXXXXD", "DXXXXXXXD", "DXXXXXXXD", "DDDDBDDDD"),
                items,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34));
    }

    private CustomMenuItem defaultIconItem() {
        return new CustomMenuItem(Material.PAPER, "%icon_name%", List.of("<gray>ID: %icon_id%", "<yellow>点击选择此图标"), "", "", "", false, false);
    }

    private CustomMenuTexts defaultMenuTexts() {
        return new CustomMenuTexts(
                "<gray>未设置",
                "<green>已购买",
                "<yellow>未购买",
                "<green>已完成",
                "<red>未完成",
                "<green><!i>保存修改",
                "<green><!i>确认购买",
                "<gray>点击保存当前修改",
                "<gray>价格: %price%",
                "<red><!i>已购买",
                "<red>购买后不可再次修改",
                "<red>请先设置启用的内容并选择图标",
                "<yellow>点击确认",
                "<green>已开启",
                "<red>已关闭"
        );
    }

    private enum MenuType {
        EDIT,
        ICON
    }

    private enum InputType {
        PREFIX,
        SUFFIX
    }

    private record CustomMenu(String title, int size, List<String> layout, Map<Character, CustomMenuItem> items, List<Integer> iconSlots) {
    }

    private record CustomMenuItem(
            Material material,
            String name,
            List<String> lore,
            String action,
            String itemModel,
            String tooltipStyle,
            boolean glow,
            boolean useSelectedIcon
    ) {
    }

    private record CustomMenuTexts(
            String notSet,
            String statusPurchased,
            String statusNotPurchased,
            String complete,
            String incomplete,
            String confirmSaveName,
            String confirmBuyName,
            String confirmSaveLore,
            String confirmBuyLore,
            String confirmLockedName,
            String confirmLockedLore,
            String confirmIncompleteLore,
            String confirmClickLore,
            String partEnabled,
            String partDisabled
    ) {
    }

    private record CustomTagDraft(String prefix, String suffix, String iconId) {
        private CustomTagDraft withPrefix(String value) {
            return new CustomTagDraft(value, suffix, iconId);
        }

        private CustomTagDraft withSuffix(String value) {
            return new CustomTagDraft(prefix, value, iconId);
        }

        private CustomTagDraft withIconId(String value) {
            return new CustomTagDraft(prefix, suffix, value);
        }

        private boolean isComplete(boolean requirePrefix, boolean requireSuffix) {
            return (!requirePrefix || (prefix != null && !prefix.isEmpty()))
                    && (!requireSuffix || (suffix != null && !suffix.isEmpty()))
                    && iconId != null && !iconId.isEmpty();
        }

        private CustomTag toCustomTag(String id, boolean savePrefix, boolean saveSuffix, String paymentProvider, double purchasePrice) {
            return new CustomTag(id, savePrefix ? prefix : "", saveSuffix ? suffix : "", iconId, "", paymentProvider, purchasePrice);
        }
    }

    public record CustomTagIcon(
            String id,
            String name,
            Material material,
            int customModelData,
            String itemModel,
            String tooltipStyle
    ) {
    }
}
