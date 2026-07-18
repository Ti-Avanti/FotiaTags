package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.particle.ParticleEffect;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.TextComponentParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TagEditorManager implements Listener {

    private final FotiaTags plugin;
    private final Map<UUID, EditorSession> editorSessions = new HashMap<>();
    private final Map<UUID, ChatInputSession> chatInputSessions = new HashMap<>();
    private final Set<UUID> reopening = new HashSet<>();

    private EditorMenu listMenu;
    private EditorMenu editMenu;
    private EditorMenu deleteMenu;
    private EditorMenu particlesMenu;
    private String editEmptyValueText = "<gray>(空)";
    private List<Integer> listTagSlots = new ArrayList<>();
    private EditorMenuItem listTagItem;
    private List<Integer> particleSlots = new ArrayList<>();
    private EditorMenuItem particleItem;
    private EditorMenuItem noParticleItem;
    private int prevPageSlot = 45;
    private int nextPageSlot = 53;
    private EditorMenuItem prevPageItem;
    private EditorMenuItem nextPageItem;
    private EditorMenuItem prevPageDisabledItem;
    private EditorMenuItem nextPageDisabledItem;

    public TagEditorManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        loadMenus();
    }

    private void loadMenus() {
        loadListMenu();
        loadEditMenu();
        this.deleteMenu = loadMenu("menus/tag-editor-delete.yml", defaultDeleteMenu());
        loadParticleMenu();
    }

    private void loadParticleMenu() {
        File file = new File(plugin.getDataFolder(), "menus/tag-editor-particles.yml");
        if (!file.exists()) {
            plugin.saveResource("menus/tag-editor-particles.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.particlesMenu = loadMenu(config, defaultParticlesMenu());
        this.particleSlots = cleanSlots(config.getIntegerList("particle-slots"), particlesMenu.size());
        if (particleSlots.isEmpty()) {
            this.particleSlots = cleanSlots(Arrays.asList(
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34
            ), particlesMenu.size());
        }

        ConfigurationSection particleItemSection = config.getConfigurationSection("particle-item");
        this.particleItem = particleItemSection != null ? loadMenuItem(particleItemSection) : defaultParticleItem();

        ConfigurationSection noParticleSection = config.getConfigurationSection("no-particle-item");
        this.noParticleItem = noParticleSection != null ? loadMenuItem(noParticleSection) : defaultNoParticleItem();
    }

    private void loadEditMenu() {
        File file = new File(plugin.getDataFolder(), "menus/tag-editor-edit.yml");
        if (!file.exists()) {
            plugin.saveResource("menus/tag-editor-edit.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.editEmptyValueText = config.getString("empty-value", "<gray>(空)");
        this.editMenu = loadMenu(config, defaultEditMenu());
    }

    private void loadListMenu() {
        File file = new File(plugin.getDataFolder(), "menus/tag-editor-list.yml");
        if (!file.exists()) {
            plugin.saveResource("menus/tag-editor-list.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        EditorMenu fallback = defaultListMenu();
        this.listMenu = loadMenu(config, fallback);

        List<Integer> slots = cleanSlots(config.getIntegerList("tag-slots"), listMenu.size());
        this.listTagSlots = slots.isEmpty() ? cleanSlots(Arrays.asList(
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        ), listMenu.size()) : slots;

        ConfigurationSection tagItemSection = config.getConfigurationSection("tag-item");
        this.listTagItem = tagItemSection != null ? loadMenuItem(tagItemSection) : defaultListTagItem();

        ConfigurationSection paginationSection = config.getConfigurationSection("pagination");
        if (paginationSection != null) {
            this.prevPageSlot = normalizeSlot(paginationSection.getInt("prev-page-slot", 45), Math.min(45, listMenu.size() - 1), listMenu.size());
            this.nextPageSlot = normalizeSlot(paginationSection.getInt("next-page-slot", 53), listMenu.size() - 1, listMenu.size());
            this.prevPageItem = loadOptionalItem(paginationSection, "prev-page", defaultPrevPageItem());
            this.nextPageItem = loadOptionalItem(paginationSection, "next-page", defaultNextPageItem());
            this.prevPageDisabledItem = loadOptionalItem(paginationSection, "prev-page-disabled", defaultPrevPageDisabledItem());
            this.nextPageDisabledItem = loadOptionalItem(paginationSection, "next-page-disabled", defaultNextPageDisabledItem());
        } else {
            this.prevPageSlot = Math.min(45, listMenu.size() - 1);
            this.nextPageSlot = listMenu.size() - 1;
            this.prevPageItem = defaultPrevPageItem();
            this.nextPageItem = defaultNextPageItem();
            this.prevPageDisabledItem = defaultPrevPageDisabledItem();
            this.nextPageDisabledItem = defaultNextPageDisabledItem();
        }
    }

    private EditorMenuItem loadOptionalItem(ConfigurationSection parent, String key, EditorMenuItem fallback) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        return section != null ? loadMenuItem(section) : fallback;
    }

    private EditorMenu loadMenu(String resourcePath, EditorMenu fallback) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }

        return loadMenu(YamlConfiguration.loadConfiguration(file), fallback);
    }

    private EditorMenu loadMenu(YamlConfiguration config, EditorMenu fallback) {
        String title = config.getString("title", fallback.title());
        int size = normalizeMenuSize(config.getInt("size", fallback.size()));
        List<String> layout = config.getStringList("layout");
        if (layout.isEmpty()) {
            layout = fallback.layout();
        }

        Map<Character, EditorMenuItem> items = new LinkedHashMap<>(fallback.items());
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

        return new EditorMenu(title, size, layout, items);
    }

    private EditorMenuItem loadMenuItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }

        return new EditorMenuItem(
                material,
                section.getString("name", ""),
                section.getStringList("lore"),
                section.getString("action", ""),
                section.getString("edit-field", section.getString("edit_field", "")),
                section.getString("item-model", section.getString("item_model", "")),
                section.getString("tooltip-style", section.getString("tooltip", "")),
                section.getInt("custom-model-data", section.getInt("custom_model_data", 0)),
                section.getBoolean("glow", false),
                section.getBoolean("use-tag-material", section.getBoolean("use_tag_material", false))
        );
    }

    public void openTagListEditor(Player player, int page) {
        Map<String, Tag> tags = plugin.getTagManager().getTags();
        List<String> tagIds = new ArrayList<>(tags.keySet());

        int tagsPerPage = Math.max(1, listTagSlots.size());
        int totalPages = Math.max(1, (int) Math.ceil((double) tagIds.size() / tagsPerPage));

        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        Inventory inventory = Bukkit.createInventory(
                null,
                listMenu.size(),
                parse(applyPlaceholders(listMenu.title(), player, null, "", "", page, totalPages))
        );

        renderLayout(inventory, listMenu, player, null, page, totalPages);

        int startIndex = page * tagsPerPage;
        for (int i = 0; i < listTagSlots.size(); i++) {
            int tagIndex = startIndex + i;
            if (tagIndex >= tagIds.size()) {
                break;
            }

            Tag tag = tags.get(tagIds.get(tagIndex));
            if (tag == null) {
                continue;
            }

            ItemStack item = createConfiguredItem(listTagItem, player, tag, "", "", page, totalPages);
            setString(item, "edit_tag_id", tag.getId());
            inventory.setItem(listTagSlots.get(i), item);
        }

        boolean hasPrevPage = page > 0;
        boolean hasNextPage = page < totalPages - 1;

        ItemStack prevItem = createConfiguredItem(hasPrevPage ? prevPageItem : prevPageDisabledItem, player, null, "", "", page, totalPages);
        if (hasPrevPage) {
            setString(prevItem, "editor_action", "prev_page");
        }
        inventory.setItem(prevPageSlot, prevItem);

        ItemStack nextItem = createConfiguredItem(hasNextPage ? nextPageItem : nextPageDisabledItem, player, null, "", "", page, totalPages);
        if (hasNextPage) {
            setString(nextItem, "editor_action", "next_page");
        }
        inventory.setItem(nextPageSlot, nextItem);

        reopening.add(player.getUniqueId());
        try {
            editorSessions.put(player.getUniqueId(), new EditorSession("tag_list", page, null));
            player.openInventory(inventory);
        } finally {
            reopening.remove(player.getUniqueId());
        }
    }

    public void openTagEditor(Player player, String tagId) {
        Tag tag = plugin.getTagManager().getTag(tagId);
        if (tag == null) {
            plugin.getMessageManager().send(player, "tag-not-found", Map.of("tag", tagId));
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                null,
                editMenu.size(),
                parse(applyPlaceholders(editMenu.title(), player, tag, "", "", 0, 1))
        );

        renderLayout(inventory, editMenu, player, tag, 0, 1);

        reopening.add(player.getUniqueId());
        try {
            editorSessions.put(player.getUniqueId(), new EditorSession("tag_edit", 0, tagId));
            player.openInventory(inventory);
        } finally {
            reopening.remove(player.getUniqueId());
        }
    }

    private void openConfirmDelete(Player player, String tagId) {
        Tag tag = plugin.getTagManager().getTag(tagId);

        Inventory inventory = Bukkit.createInventory(
                null,
                deleteMenu.size(),
                parse(applyPlaceholders(deleteMenu.title(), player, tag, "", "", 0, 1))
        );

        renderLayout(inventory, deleteMenu, player, tag, 0, 1);

        reopening.add(player.getUniqueId());
        try {
            editorSessions.put(player.getUniqueId(), new EditorSession("confirm_delete", 0, tagId));
            player.openInventory(inventory);
        } finally {
            reopening.remove(player.getUniqueId());
        }
    }

    private void openParticleSelector(Player player, String tagId) {
        Tag tag = plugin.getTagManager().getTag(tagId);
        if (tag == null) {
            plugin.getMessageManager().send(player, "tag-not-found", Map.of("tag", tagId));
            return;
        }

        Inventory inventory = Bukkit.createInventory(
                null,
                particlesMenu.size(),
                parse(applyPlaceholders(particlesMenu.title(), player, tag, "", "", 0, 1))
        );

        renderLayout(inventory, particlesMenu, player, tag, 0, 1);

        int index = 0;
        if (index < particleSlots.size()) {
            boolean selected = tag.getParticleEffect().isBlank();
            ItemStack item = createConfiguredItem(noParticleItem, player, tag, "", "", 0, 1, "", "无粒子", selected);
            setString(item, "editor_action", "clear_particle");
            setString(item, "particle_effect_id", "");
            inventory.setItem(particleSlots.get(index++), item);
        }

        if (plugin.getParticleManager() != null) {
            for (ParticleEffect effect : plugin.getParticleManager().getEffects()) {
                if (index >= particleSlots.size()) {
                    break;
                }

                boolean selected = effect.id().equalsIgnoreCase(tag.getParticleEffect());
                EditorMenuItem itemConfig = withParticleGuiItem(particleItem, effect);
                ItemStack item = createConfiguredItem(itemConfig, player, tag, "", "", 0, 1, effect.id(), effect.displayName(), selected);
                setString(item, "editor_action", "select_particle");
                setString(item, "particle_effect_id", effect.id());
                inventory.setItem(particleSlots.get(index++), item);
            }
        }

        reopening.add(player.getUniqueId());
        try {
            editorSessions.put(player.getUniqueId(), new EditorSession("particle_select", 0, tagId));
            player.openInventory(inventory);
        } finally {
            reopening.remove(player.getUniqueId());
        }
    }

    private void renderLayout(Inventory inventory, EditorMenu menu, Player player, Tag tag, int page, int totalPages) {
        for (int row = 0; row < menu.layout().size() && row < menu.size() / 9; row++) {
            String rowLayout = menu.layout().get(row);
            for (int col = 0; col < rowLayout.length() && col < 9; col++) {
                char key = rowLayout.charAt(col);
                if (key == ' ') {
                    continue;
                }

                EditorMenuItem itemConfig = menu.items().get(key);
                if (itemConfig == null) {
                    continue;
                }

                int slot = row * 9 + col;
                inventory.setItem(slot, createConfiguredItem(itemConfig, player, tag, "", "", page, totalPages));
            }
        }
    }

    private ItemStack createConfiguredItem(EditorMenuItem config, Player player, Tag tag, String currentValue, String fieldName, int page, int totalPages) {
        return createConfiguredItem(config, player, tag, currentValue, fieldName, page, totalPages, "", "", false);
    }

    private ItemStack createConfiguredItem(EditorMenuItem config, Player player, Tag tag, String currentValue, String fieldName, int page, int totalPages,
                                           String particleId, String particleName, boolean particleSelected) {
        String editField = config.editField();
        String actualCurrentValue = editField == null || editField.isEmpty() ? currentValue : getTagFieldValue(tag, editField);
        String actualFieldName = editField == null || editField.isEmpty() ? fieldName : getFieldDisplayName(editField);

        Material material = config.useTagMaterial() && tag != null && tag.getMaterial() != null ? tag.getMaterial() : config.material();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = applyPlaceholders(config.name(), player, tag, actualCurrentValue, actualFieldName, page, totalPages, particleId, particleName, particleSelected);
        if (!name.isEmpty()) {
            meta.displayName(parse(name));
        }

        if (!config.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : config.lore()) {
                String parsedLine = applyPlaceholders(line, player, tag, actualCurrentValue, actualFieldName, page, totalPages, particleId, particleName, particleSelected);
                lore.add(parsedLine.isEmpty() ? Component.empty() : parse(parsedLine));
            }
            meta.lore(lore);
        }

        String itemModel = config.itemModel();
        if ((itemModel == null || itemModel.isEmpty()) && config.useTagMaterial() && tag != null) {
            itemModel = tag.getItemModel();
        }
        if (itemModel != null && !itemModel.isEmpty()) {
            setItemModelCompat(meta, applyPlaceholders(itemModel, player, tag, actualCurrentValue, actualFieldName, page, totalPages, particleId, particleName, particleSelected));
        }

        String tooltipStyle = config.tooltipStyle();
        if ((tooltipStyle == null || tooltipStyle.isEmpty()) && config.useTagMaterial() && tag != null) {
            tooltipStyle = tag.getTooltipStyle();
        }
        if (tooltipStyle != null && !tooltipStyle.isEmpty()) {
            setTooltipStyleCompat(meta, applyPlaceholders(tooltipStyle, player, tag, actualCurrentValue, actualFieldName, page, totalPages, particleId, particleName, particleSelected));
        }

        int customModelData = config.customModelData();
        if (customModelData <= 0 && config.useTagMaterial() && tag != null) {
            customModelData = tag.getCustomModelData();
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        if (config.glow()) {
            setGlowCompat(meta);
        }

        if (config.action() != null && !config.action().isEmpty()) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "editor_action"), PersistentDataType.STRING, config.action());
        }
        if (editField != null && !editField.isEmpty()) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "edit_field"), PersistentDataType.STRING, editField);
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private String applyPlaceholders(String text, Player player, Tag tag, String currentValue, String fieldName, int page, int totalPages) {
        return applyPlaceholders(text, player, tag, currentValue, fieldName, page, totalPages, "", "", false);
    }

    private String applyPlaceholders(String text, Player player, Tag tag, String currentValue, String fieldName, int page, int totalPages,
                                     String particleId, String particleName, boolean particleSelected) {
        if (text == null) {
            return "";
        }

        return text
                .replace("%player%", player != null ? player.getName() : "")
                .replace("%page%", String.valueOf(page + 1))
                .replace("%total_pages%", String.valueOf(totalPages))
                .replace("%tag_id%", tag != null ? tag.getId() : "")
                .replace("%tag_name%", tag != null ? color(tag.getDisplayName()) : "")
                .replace("%tag_prefix%", tag != null ? color(tag.getPrefix()) : "")
                .replace("%tag_suffix%", tag != null ? color(tag.getSuffix()) : "")
                .replace("%tag_prefix2%", tag != null ? color(tag.getPrefix2()) : "")
                .replace("%tag_suffix2%", tag != null ? color(tag.getSuffix2()) : "")
                .replace("%tag_permission%", tag != null && tag.getPermission() != null ? tag.getPermission() : "")
                .replace("%tag_material%", tag != null && tag.getMaterial() != null ? tag.getMaterial().name() : "")
                .replace("%tag_item_model%", tag != null && tag.getItemModel() != null ? tag.getItemModel() : "")
                .replace("%tag_tooltip_style%", tag != null && tag.getTooltipStyle() != null ? tag.getTooltipStyle() : "")
                .replace("%tag_custom_model_data%", tag != null ? String.valueOf(tag.getCustomModelData()) : "0")
                .replace("%tag_particle%", tag != null ? color(emptyText(tag.getParticleEffect())) : "")
                .replace("%particle_id%", particleId != null ? particleId : "")
                .replace("%particle_name%", particleName != null ? color(particleName) : "")
                .replace("%particle_selected%", particleSelected ? "<green>已选择" : "<gray>未选择")
                .replace("%field%", fieldName != null ? fieldName : "")
                .replace("%field_name%", fieldName != null ? fieldName : "")
                .replace("%current_value%", color(emptyText(currentValue)))
                .replace("%raw_current_value%", currentValue != null ? currentValue : "");
    }

    private String getTagFieldValue(Tag tag, String field) {
        if (tag == null) {
            return "";
        }

        return switch (field) {
            case "display_name" -> tag.getDisplayName();
            case "prefix" -> tag.getPrefix();
            case "suffix" -> tag.getSuffix();
            case "prefix2" -> tag.getPrefix2();
            case "suffix2" -> tag.getSuffix2();
            case "permission" -> tag.getPermission() != null ? tag.getPermission() : "";
            case "material" -> tag.getMaterial() != null ? tag.getMaterial().name() : "";
            case "item_model" -> tag.getItemModel() != null ? tag.getItemModel() : "";
            case "tooltip_style" -> tag.getTooltipStyle() != null ? tag.getTooltipStyle() : "";
            case "custom_model_data" -> String.valueOf(tag.getCustomModelData());
            case "particle" -> tag.getParticleEffect();
            default -> "";
        };
    }

    private String getFieldDisplayName(String field) {
        return switch (field) {
            case "display_name" -> "显示名称";
            case "prefix" -> "前缀 (MiniMessage格式)";
            case "suffix" -> "后缀 (MiniMessage格式)";
            case "prefix2" -> "前缀2 (PAPI格式)";
            case "suffix2" -> "后缀2 (PAPI格式)";
            case "permission" -> "权限节点";
            case "material" -> "物品材质 (如 PAPER, DIAMOND)";
            case "item_model" -> "物品模型 (如 fotia:xxx)";
            case "tooltip_style" -> "提示样式 (如 minecraft:rarity/legendary)";
            case "custom_model_data" -> "自定义模型数据 (数字)";
            case "particle" -> "粒子效果";
            default -> field;
        };
    }

    private String emptyText(String text) {
        return text == null || text.isEmpty() ? editEmptyValueText : text;
    }

    private String color(String text) {
        String converted = LegacyColorConverter.convertToMiniMessage(text);
        return converted != null ? converted : "";
    }

    private Component parse(String text) {
        return TextComponentParser.parse(text);
    }

    private void setString(ItemStack item, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    private String getAction(ItemStack item) {
        return getString(item, "editor_action");
    }

    private String getEditField(ItemStack item) {
        return getString(item, "edit_field");
    }

    private String getEditTagId(ItemStack item) {
        return getString(item, "edit_tag_id");
    }

    private String getParticleEffectId(ItemStack item) {
        return getString(item, "particle_effect_id");
    }

    private String getString(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        EditorSession session = editorSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String action = getAction(clicked);
        String editField = getEditField(clicked);
        String editTagId = getEditTagId(clicked);
        String particleEffectId = getParticleEffectId(clicked);

        if (session.menuType.equals("tag_list")) {
            handleTagListClick(player, session, action, editTagId);
        } else if (session.menuType.equals("tag_edit")) {
            handleTagEditClick(player, session, action, editField);
        } else if (session.menuType.equals("confirm_delete")) {
            handleConfirmDeleteClick(player, session, action);
        } else if (session.menuType.equals("particle_select")) {
            handleParticleSelectClick(player, session, action, particleEffectId);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (editorSessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void handleTagListClick(Player player, EditorSession session, String action, String editTagId) {
        if (editTagId != null) {
            openTagEditor(player, editTagId);
            return;
        }

        if (action == null) {
            return;
        }

        switch (action) {
            case "prev_page" -> openTagListEditor(player, session.page - 1);
            case "next_page" -> openTagListEditor(player, session.page + 1);
            case "create_tag" -> startCreateTag(player);
            default -> {
            }
        }
    }

    private void handleTagEditClick(Player player, EditorSession session, String action, String editField) {
        if (editField != null) {
            startEditField(player, session.editingTagId, editField);
            return;
        }

        if (action == null) {
            return;
        }

        switch (action) {
            case "back_to_list" -> openTagListEditor(player, 0);
            case "save_tag" -> {
                saveTagConfig();
                plugin.getTagManager().loadTags();
                plugin.getMessageManager().send(player, "tag-editor-saved");
                openTagListEditor(player, 0);
            }
            case "delete_tag" -> openConfirmDelete(player, session.editingTagId);
            case "select_particle_menu" -> openParticleSelector(player, session.editingTagId);
            default -> {
            }
        }
    }

    private void handleConfirmDeleteClick(Player player, EditorSession session, String action) {
        if (action == null) {
            return;
        }

        switch (action) {
            case "confirm_delete" -> {
                deleteTag(session.editingTagId);
                plugin.getMessageManager().send(player, "tag-editor-deleted");
                openTagListEditor(player, 0);
            }
            case "cancel_delete" -> openTagEditor(player, session.editingTagId);
            default -> {
            }
        }
    }

    private void handleParticleSelectClick(Player player, EditorSession session, String action, String particleEffectId) {
        if (action == null) {
            return;
        }

        switch (action) {
            case "back_to_edit" -> openTagEditor(player, session.editingTagId);
            case "clear_particle" -> {
                setTagParticle(session.editingTagId, "");
                plugin.getMessageManager().send(player, "tag-editor-particle-cleared");
                openTagEditor(player, session.editingTagId);
            }
            case "select_particle" -> {
                if (particleEffectId == null || particleEffectId.isBlank()) {
                    return;
                }
                setTagParticle(session.editingTagId, particleEffectId);
                plugin.getMessageManager().send(player, "tag-editor-particle-set",
                        MessageManager.of("particle", particleEffectId));
                openTagEditor(player, session.editingTagId);
            }
            default -> {
            }
        }
    }

    private void startCreateTag(Player player) {
        player.closeInventory();
        chatInputSessions.put(player.getUniqueId(), new ChatInputSession(null, "new_tag_id"));
        plugin.getMessageManager().send(player, "tag-editor-create-prompt");
        plugin.getMessageManager().send(player, "tag-editor-input-cancel");
    }

    private void startEditField(Player player, String tagId, String field) {
        player.closeInventory();
        chatInputSessions.put(player.getUniqueId(), new ChatInputSession(tagId, field));

        String fieldName = getFieldDisplayName(field);
        plugin.getMessageManager().send(player, "tag-editor-edit-prompt", MessageManager.of("field", fieldName));
        plugin.getMessageManager().send(player, "tag-editor-input-controls");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatInputSession inputSession = chatInputSessions.remove(player.getUniqueId());
        if (inputSession == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (inputSession.tagId == null) {
                    openTagListEditor(player, 0);
                } else {
                    openTagEditor(player, inputSession.tagId);
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inputSession.field.equals("new_tag_id")) {
                handleCreateNewTag(player, input);
            } else {
                handleEditField(player, inputSession.tagId, inputSession.field, input);
            }
        });
    }

    private void handleCreateNewTag(Player player, String tagId) {
        if (!tagId.matches("^[a-zA-Z0-9_]+$")) {
            plugin.getMessageManager().send(player, "tag-editor-invalid-id");
            Bukkit.getScheduler().runTask(plugin, () -> openTagListEditor(player, 0));
            return;
        }

        if (plugin.getTagManager().getTag(tagId) != null) {
            plugin.getMessageManager().send(player, "tag-editor-id-exists");
            Bukkit.getScheduler().runTask(plugin, () -> openTagListEditor(player, 0));
            return;
        }

        YamlConfiguration tagsConfig = (YamlConfiguration) plugin.getConfigManager().getTagsConfig();
        tagsConfig.set("tags." + tagId + ".display-name", tagId);
        tagsConfig.set("tags." + tagId + ".prefix", "");
        tagsConfig.set("tags." + tagId + ".suffix", "");
        tagsConfig.set("tags." + tagId + ".prefix2", "");
        tagsConfig.set("tags." + tagId + ".suffix2", "");
        tagsConfig.set("tags." + tagId + ".permission", "");
        tagsConfig.set("tags." + tagId + ".material", "PAPER");
        tagsConfig.set("tags." + tagId + ".item-model", "");
        tagsConfig.set("tags." + tagId + ".tooltip-style", "");
        tagsConfig.set("tags." + tagId + ".custom-model-data", 0);
        tagsConfig.set("tags." + tagId + ".particle", "");

        saveTagConfig();
        plugin.getTagManager().loadTags();

        plugin.getMessageManager().send(player, "tag-editor-created", MessageManager.of("tag", tagId));
        Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
    }

    private void handleEditField(Player player, String tagId, String field, String input) {
        YamlConfiguration tagsConfig = (YamlConfiguration) plugin.getConfigManager().getTagsConfig();
        String configPath = "tags." + tagId + ".";
        String value = input.equalsIgnoreCase("clear") ? "" : input;

        switch (field) {
            case "display_name" -> tagsConfig.set(configPath + "display-name", value);
            case "prefix" -> tagsConfig.set(configPath + "prefix", value);
            case "suffix" -> tagsConfig.set(configPath + "suffix", value);
            case "prefix2" -> tagsConfig.set(configPath + "prefix2", value);
            case "suffix2" -> tagsConfig.set(configPath + "suffix2", value);
            case "permission" -> tagsConfig.set(configPath + "permission", value);
            case "material" -> {
                Material mat = Material.matchMaterial(value.toUpperCase());
                if (mat == null && !value.isEmpty()) {
                    plugin.getMessageManager().send(player, "tag-editor-invalid-material");
                    Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
                    return;
                }
                tagsConfig.set(configPath + "material", mat != null ? mat.name() : "PAPER");
            }
            case "item_model" -> tagsConfig.set(configPath + "item-model", value);
            case "tooltip_style" -> tagsConfig.set(configPath + "tooltip-style", value);
            case "particle" -> tagsConfig.set(configPath + "particle", value);
            case "custom_model_data" -> {
                try {
                    int cmd = value.isEmpty() ? 0 : Integer.parseInt(value);
                    tagsConfig.set(configPath + "custom-model-data", cmd);
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().send(player, "tag-editor-invalid-number");
                    Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
                    return;
                }
            }
            default -> {
                return;
            }
        }

        saveTagConfig();
        plugin.getTagManager().loadTags();

        plugin.getMessageManager().send(player, "tag-editor-updated");
        Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
    }

    private void setTagParticle(String tagId, String particleEffectId) {
        YamlConfiguration tagsConfig = (YamlConfiguration) plugin.getConfigManager().getTagsConfig();
        tagsConfig.set("tags." + tagId + ".particle", particleEffectId != null ? particleEffectId : "");
        saveTagConfig();
        plugin.getTagManager().loadTags();
    }

    private void deleteTag(String tagId) {
        YamlConfiguration tagsConfig = (YamlConfiguration) plugin.getConfigManager().getTagsConfig();
        tagsConfig.set("tags." + tagId, null);
        saveTagConfig();
        plugin.getTagManager().loadTags();
    }

    private void saveTagConfig() {
        try {
            File file = new File(plugin.getDataFolder(), "tags.yml");
            ((YamlConfiguration) plugin.getConfigManager().getTagsConfig()).save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tags.yml: " + e.getMessage());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (reopening.contains(player.getUniqueId()) || chatInputSessions.containsKey(player.getUniqueId())) {
                return;
            }
            editorSessions.remove(player.getUniqueId());
        }
    }

    public boolean isInChatInput(UUID uuid) {
        return chatInputSessions.containsKey(uuid);
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

    private int normalizeSlot(int slot, int fallback, int menuSize) {
        if (slot >= 0 && slot < menuSize) {
            return slot;
        }
        return Math.max(0, Math.min(fallback, menuSize - 1));
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

    private void setItemModelCompat(ItemMeta meta, String itemModel) {
        GuiItemMetaCompat.setItemModel(meta, itemModel);
    }

    private void setTooltipStyleCompat(ItemMeta meta, String tooltip) {
        GuiItemMetaCompat.setTooltipStyle(meta, tooltip);
    }

    private void setGlowCompat(ItemMeta meta) {
        GuiItemMetaCompat.setGlow(meta);
    }

    private EditorMenuItem withParticleGuiItem(EditorMenuItem template, ParticleEffect effect) {
        return new EditorMenuItem(
                effect.guiMaterial(),
                template.name(),
                template.lore(),
                template.action(),
                template.editField(),
                firstNonBlank(effect.itemModel(), template.itemModel()),
                firstNonBlank(effect.tooltipStyle(), template.tooltipStyle()),
                effect.customModelData() > 0 ? effect.customModelData() : template.customModelData(),
                template.glow() || effect.glow(),
                false
        );
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private EditorMenu defaultListMenu() {
        Map<Character, EditorMenuItem> items = new LinkedHashMap<>();
        items.put('X', simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", ""));
        items.put('C', simpleItem(Material.EMERALD, "<green><!i>创建新称号", List.of("<gray>点击创建一个新称号"), "create_tag", ""));
        return new EditorMenu("<gold><!i>称号编辑器 <gray>(%page%/%total_pages%)", 54,
                List.of("XXXXXXXXX", "X       X", "X       X", "X       X", "X       X", "XXXXCXXXX"), items);
    }

    private EditorMenu defaultEditMenu() {
        Map<Character, EditorMenuItem> items = new LinkedHashMap<>();
        items.put('X', simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", ""));
        items.put('N', simpleItem(Material.NAME_TAG, "<white><!i>称号ID", List.of("<yellow>%tag_id%", "", "<gray>称号ID不可修改"), "", ""));
        items.put('D', simpleItem(Material.OAK_SIGN, "<white><!i>显示名称", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "display_name"));
        items.put('P', simpleItem(Material.PAPER, "<white><!i>前缀 (MiniMessage)", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "prefix"));
        items.put('S', simpleItem(Material.PAPER, "<white><!i>后缀 (MiniMessage)", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "suffix"));
        items.put('A', simpleItem(Material.MAP, "<white><!i>前缀2 (PAPI)", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "prefix2"));
        items.put('B', simpleItem(Material.MAP, "<white><!i>后缀2 (PAPI)", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "suffix2"));
        items.put('R', simpleItem(Material.IRON_BARS, "<white><!i>权限节点", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "permission"));
        items.put('M', new EditorMenuItem(Material.PAPER, "<white><!i>GUI物品材质", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "material", "", "", 0, false, true));
        items.put('I', simpleItem(Material.ITEM_FRAME, "<white><!i>物品模型 (item-model)", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "item_model"));
        items.put('O', simpleItem(Material.COMMAND_BLOCK, "<white><!i>自定义模型数据", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "custom_model_data"));
        items.put('T', simpleItem(Material.PAINTING, "<white><!i>提示样式 (tooltip-style)", List.of("<gray>当前值:", "<white>%current_value%", "", "<yellow>点击修改"), "", "tooltip_style"));
        items.put('L', simpleItem(Material.ARROW, "<yellow><!i>返回列表", List.of("<gray>点击返回称号列表"), "back_to_list", ""));
        items.put('V', simpleItem(Material.LIME_CONCRETE, "<green><!i>保存更改", List.of("<gray>点击保存所有更改"), "save_tag", ""));
        items.put('E', simpleItem(Material.RED_CONCRETE, "<red><!i>删除称号", List.of("<gray>点击删除此称号", "", "<red>此操作不可撤销！"), "delete_tag", ""));
        return new EditorMenu("<gold><!i>编辑称号: %tag_name%", 54,
                List.of("XXXXNXXXX", "XXXXXXXXX", "XDPSABRMX", "XXIOTXXXX", "XXXXXXXXX", "LXXXVXXXE"), items);
    }

    private EditorMenu defaultDeleteMenu() {
        Map<Character, EditorMenuItem> items = new LinkedHashMap<>();
        items.put('X', simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", ""));
        items.put('Y', simpleItem(Material.RED_CONCRETE, "<red><!i>确认删除", List.of("<gray>点击确认删除此称号", "", "<red>此操作不可撤销！"), "confirm_delete", ""));
        items.put('N', simpleItem(Material.LIME_CONCRETE, "<green><!i>取消", List.of("<gray>点击返回编辑界面"), "cancel_delete", ""));
        return new EditorMenu("<red><!i>确认删除: %tag_name%", 27, List.of("XXXXXXXXX", "XXYXXXNXX", "XXXXXXXXX"), items);
    }

    private EditorMenu defaultParticlesMenu() {
        Map<Character, EditorMenuItem> items = new LinkedHashMap<>();
        items.put('X', simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), "", ""));
        items.put('B', simpleItem(Material.ARROW, "<yellow><!i>返回编辑菜单", List.of("<gray>点击返回称号编辑菜单"), "back_to_edit", ""));
        return new EditorMenu("<gold><!i>选择粒子效果: %tag_name%", 54,
                List.of("XXXXXXXXX", "X       X", "X       X", "X       X", "X       X", "BXXXXXXXX"), items);
    }

    private EditorMenuItem defaultParticleItem() {
        return new EditorMenuItem(Material.BLAZE_POWDER, "<white><!i>%particle_name%", List.of(
                "<gray>ID: <yellow>%particle_id%",
                "<gray>状态: %particle_selected%",
                "",
                "<yellow>点击绑定此粒子"
        ), "select_particle", "", "", "", 0, false, false);
    }

    private EditorMenuItem defaultNoParticleItem() {
        return new EditorMenuItem(Material.BARRIER, "<red><!i>不设置粒子", List.of(
                "<gray>当前状态: %particle_selected%",
                "",
                "<yellow>点击清空粒子效果"
        ), "clear_particle", "", "", "", 0, false, false);
    }

    private EditorMenuItem defaultListTagItem() {
        return new EditorMenuItem(Material.PAPER, "<white><!i>%tag_name%", List.of(
                "<gray>ID: <yellow>%tag_id%",
                "<gray>前缀: <white>%tag_prefix%",
                "<gray>后缀: <white>%tag_suffix%",
                "",
                "<yellow>点击编辑此称号"
        ), "", "", "", "", 0, false, true);
    }

    private EditorMenuItem defaultPrevPageItem() {
        return simpleItem(Material.ARROW, "<yellow><!i>上一页", List.of("<gray>点击返回上一页"), "", "");
    }

    private EditorMenuItem defaultNextPageItem() {
        return simpleItem(Material.ARROW, "<yellow><!i>下一页", List.of("<gray>点击前往下一页"), "", "");
    }

    private EditorMenuItem defaultPrevPageDisabledItem() {
        return simpleItem(Material.GRAY_DYE, "<gray><!i>没有上一页", List.of(), "", "");
    }

    private EditorMenuItem defaultNextPageDisabledItem() {
        return simpleItem(Material.GRAY_DYE, "<gray><!i>没有下一页", List.of(), "", "");
    }

    private EditorMenuItem simpleItem(Material material, String name, List<String> lore, String action, String editField) {
        return new EditorMenuItem(material, name, lore, action, editField, "", "", 0, false, false);
    }

    private static class EditorSession {
        String menuType;
        int page;
        String editingTagId;

        EditorSession(String menuType, int page, String editingTagId) {
            this.menuType = menuType;
            this.page = page;
            this.editingTagId = editingTagId;
        }
    }

    private static class ChatInputSession {
        String tagId;
        String field;

        ChatInputSession(String tagId, String field) {
            this.tagId = tagId;
            this.field = field;
        }
    }

    private record EditorMenu(String title, int size, List<String> layout, Map<Character, EditorMenuItem> items) {
    }

    private record EditorMenuItem(
            Material material,
            String name,
            List<String> lore,
            String action,
            String editField,
            String itemModel,
            String tooltipStyle,
            int customModelData,
            boolean glow,
            boolean useTagMaterial
    ) {
    }
}
