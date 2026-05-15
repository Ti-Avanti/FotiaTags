package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.Tag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
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
import java.util.*;

public class TagEditorManager implements Listener {

    private final FotiaTags plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, EditorSession> editorSessions = new HashMap<>();
    private final Map<UUID, ChatInputSession> chatInputSessions = new HashMap<>();
    private final Set<UUID> reopening = new HashSet<>();

    // 编辑器菜单槽位（4行）
    private static final int[] TAG_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int CREATE_TAG_SLOT = 49;

    public TagEditorManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 打开称号列表编辑器
     */
    public void openTagListEditor(Player player, int page) {
        Map<String, Tag> tags = plugin.getTagManager().getTags();
        List<String> tagIds = new ArrayList<>(tags.keySet());

        int tagsPerPage = TAG_SLOTS.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) tagIds.size() / tagsPerPage));

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        String title = "<gold><!i>称号编辑器";
        if (totalPages > 1) {
            title += " <gray>(" + (page + 1) + "/" + totalPages + ")";
        }

        Inventory inv = Bukkit.createInventory(null, 54, miniMessage.deserialize(title));

        // 填充边框
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        inv.setItem(9, border);
        inv.setItem(17, border);
        inv.setItem(18, border);
        inv.setItem(26, border);
        inv.setItem(27, border);
        inv.setItem(35, border);
        inv.setItem(36, border);
        inv.setItem(44, border);

        // 填充称号
        int startIndex = page * tagsPerPage;
        for (int i = 0; i < TAG_SLOTS.length; i++) {
            int tagIndex = startIndex + i;
            if (tagIndex >= tagIds.size()) break;

            String tagId = tagIds.get(tagIndex);
            Tag tag = tags.get(tagId);
            if (tag == null) continue;

            ItemStack item = createTagEditorItem(tag);
            inv.setItem(TAG_SLOTS[i], item);
        }

        // 翻页按钮
        if (page > 0) {
            ItemStack prev = createItem(Material.ARROW, "<yellow><!i>上一页", "<gray>点击返回上一页");
            setAction(prev, "prev_page");
            inv.setItem(PREV_PAGE_SLOT, prev);
        }

        if (page < totalPages - 1) {
            ItemStack next = createItem(Material.ARROW, "<yellow><!i>下一页", "<gray>点击前往下一页");
            setAction(next, "next_page");
            inv.setItem(NEXT_PAGE_SLOT, next);
        }

        // 创建新称号按钮
        ItemStack create = createItem(Material.EMERALD, "<green><!i>创建新称号", "<gray>点击创建一个新称号");
        setAction(create, "create_tag");
        inv.setItem(CREATE_TAG_SLOT, create);

        reopening.add(player.getUniqueId());
        editorSessions.put(player.getUniqueId(), new EditorSession("tag_list", page, null));
        player.openInventory(inv);
        reopening.remove(player.getUniqueId());
    }

    /**
     * 打开单个称号编辑器
     */
    public void openTagEditor(Player player, String tagId) {
        Tag tag = plugin.getTagManager().getTag(tagId);
        if (tag == null) {
            plugin.getMessageManager().send(player, "tag-not-found", Map.of("tag", tagId));
            return;
        }

        String title = "<gold><!i>编辑称号: " + tag.getDisplayName();
        Inventory inv = Bukkit.createInventory(null, 54, miniMessage.deserialize(title));

        // 填充边框
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // 称号ID（不可编辑）
        inv.setItem(4, createInfoItem(Material.NAME_TAG, "<white><!i>称号ID", "<yellow>" + tagId, "", "<gray>称号ID不可修改"));

        // 显示名称
        inv.setItem(19, createEditItem(Material.OAK_SIGN, "<white><!i>显示名称", tag.getDisplayName(), "display_name"));

        // 前缀
        inv.setItem(20, createEditItem(Material.PAPER, "<white><!i>前缀 (MiniMessage)", tag.getPrefix(), "prefix"));

        // 后缀
        inv.setItem(21, createEditItem(Material.PAPER, "<white><!i>后缀 (MiniMessage)", tag.getSuffix(), "suffix"));

        // 前缀2
        inv.setItem(22, createEditItem(Material.MAP, "<white><!i>前缀2 (PAPI)", tag.getPrefix2(), "prefix2"));

        // 后缀2
        inv.setItem(23, createEditItem(Material.MAP, "<white><!i>后缀2 (PAPI)", tag.getSuffix2(), "suffix2"));

        // 权限
        inv.setItem(24, createEditItem(Material.IRON_BARS, "<white><!i>权限节点", tag.getPermission() != null ? tag.getPermission() : "", "permission"));

        // 物品材质
        inv.setItem(25, createEditItem(tag.getMaterial(), "<white><!i>GUI物品材质", tag.getMaterial().name(), "material"));

        // item-model
        inv.setItem(29, createEditItem(Material.ITEM_FRAME, "<white><!i>物品模型 (item-model)", tag.getItemModel() != null ? tag.getItemModel() : "", "item_model"));

        // custom-model-data
        inv.setItem(30, createEditItem(Material.COMMAND_BLOCK, "<white><!i>自定义模型数据", String.valueOf(tag.getCustomModelData()), "custom_model_data"));

        // tooltip-style
        inv.setItem(31, createEditItem(Material.PAINTING, "<white><!i>提示样式 (tooltip-style)", tag.getTooltipStyle() != null ? tag.getTooltipStyle() : "", "tooltip_style"));

        // 删除按钮
        ItemStack delete = createItem(Material.RED_CONCRETE, "<red><!i>删除称号", "<gray>点击删除此称号", "", "<red>此操作不可撤销！");
        setAction(delete, "delete_tag");
        inv.setItem(53, delete);

        // 返回按钮
        ItemStack back = createItem(Material.ARROW, "<yellow><!i>返回列表", "<gray>点击返回称号列表");
        setAction(back, "back_to_list");
        inv.setItem(45, back);

        // 保存按钮
        ItemStack save = createItem(Material.LIME_CONCRETE, "<green><!i>保存更改", "<gray>点击保存所有更改");
        setAction(save, "save_tag");
        inv.setItem(49, save);

        reopening.add(player.getUniqueId());
        editorSessions.put(player.getUniqueId(), new EditorSession("tag_edit", 0, tagId));
        player.openInventory(inv);
        reopening.remove(player.getUniqueId());
    }

    private ItemStack createTagEditorItem(Tag tag) {
        Material material = tag.getMaterial() != null ? tag.getMaterial() : Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(miniMessage.deserialize("<!i><white>" + tag.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize("<!i><gray>ID: <yellow>" + tag.getId()));
        lore.add(miniMessage.deserialize("<!i><gray>前缀: <white>" + (tag.getPrefix().isEmpty() ? "(空)" : tag.getPrefix())));
        lore.add(miniMessage.deserialize("<!i><gray>后缀: <white>" + (tag.getSuffix().isEmpty() ? "(空)" : tag.getSuffix())));
        lore.add(Component.empty());
        lore.add(miniMessage.deserialize("<!i><yellow>点击编辑此称号"));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "edit_tag_id"),
                PersistentDataType.STRING,
                tag.getId()
        );

        if (tag.getCustomModelData() > 0) {
            meta.setCustomModelData(tag.getCustomModelData());
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(miniMessage.deserialize(name));

        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(miniMessage.deserialize("<!i>" + line));
            }
            meta.lore(lore);
        }

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(Material material, String name, String... loreLines) {
        return createItem(material, name, loreLines);
    }

    private ItemStack createEditItem(Material material, String name, String currentValue, String editField) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(miniMessage.deserialize(name));

        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize("<!i><gray>当前值:"));
        String displayValue = currentValue == null || currentValue.isEmpty() ? "(空)" : currentValue;
        if (displayValue.length() > 40) {
            displayValue = displayValue.substring(0, 37) + "...";
        }
        lore.add(miniMessage.deserialize("<!i><white>" + displayValue));
        lore.add(Component.empty());
        lore.add(miniMessage.deserialize("<!i><yellow>点击修改"));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "edit_field"),
                PersistentDataType.STRING,
                editField
        );

        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private void setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "editor_action"),
                    PersistentDataType.STRING,
                    action
            );
            item.setItemMeta(meta);
        }
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "editor_action"),
                PersistentDataType.STRING
        );
    }

    private String getEditField(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "edit_field"),
                PersistentDataType.STRING
        );
    }

    private String getEditTagId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "edit_tag_id"),
                PersistentDataType.STRING
        );
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        EditorSession session = editorSessions.get(player.getUniqueId());
        if (session == null) return;

        // 取消所有点击（包括玩家背包）
        event.setCancelled(true);

        // 只处理顶部菜单的点击
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String action = getAction(clicked);
        String editField = getEditField(clicked);
        String editTagId = getEditTagId(clicked);

        if (session.menuType.equals("tag_list")) {
            handleTagListClick(player, session, action, editTagId);
        } else if (session.menuType.equals("tag_edit")) {
            handleTagEditClick(player, session, action, editField);
        } else if (session.menuType.equals("confirm_delete")) {
            handleConfirmDeleteClick(player, session, action);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        EditorSession session = editorSessions.get(player.getUniqueId());
        if (session == null) return;

        // 取消所有拖动
        event.setCancelled(true);
    }

    private void handleTagListClick(Player player, EditorSession session, String action, String editTagId) {
        if (editTagId != null) {
            openTagEditor(player, editTagId);
            return;
        }

        if (action == null) return;

        switch (action) {
            case "prev_page" -> openTagListEditor(player, session.page - 1);
            case "next_page" -> openTagListEditor(player, session.page + 1);
            case "create_tag" -> startCreateTag(player);
        }
    }

    private void handleTagEditClick(Player player, EditorSession session, String action, String editField) {
        if (editField != null) {
            startEditField(player, session.editingTagId, editField);
            return;
        }

        if (action == null) return;

        switch (action) {
            case "back_to_list" -> openTagListEditor(player, 0);
            case "save_tag" -> {
                saveTagConfig();
                plugin.getTagManager().loadTags();
                player.sendMessage(miniMessage.deserialize("<green>称号配置已保存并重载！"));
                openTagListEditor(player, 0);
            }
            case "delete_tag" -> openConfirmDelete(player, session.editingTagId);
        }
    }

    private void handleConfirmDeleteClick(Player player, EditorSession session, String action) {
        if (action == null) return;

        switch (action) {
            case "confirm_delete" -> {
                deleteTag(session.editingTagId);
                player.sendMessage(miniMessage.deserialize("<green>称号已删除！"));
                openTagListEditor(player, 0);
            }
            case "cancel_delete" -> openTagEditor(player, session.editingTagId);
        }
    }

    private void openConfirmDelete(Player player, String tagId) {
        Tag tag = plugin.getTagManager().getTag(tagId);
        String tagName = tag != null ? tag.getDisplayName() : tagId;

        Inventory inv = Bukkit.createInventory(null, 27, miniMessage.deserialize("<red><!i>确认删除: " + tagName));

        // 填充边框
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        // 确认按钮
        ItemStack confirm = createItem(Material.RED_CONCRETE, "<red><!i>确认删除", "<gray>点击确认删除此称号", "", "<red>此操作不可撤销！");
        setAction(confirm, "confirm_delete");
        inv.setItem(11, confirm);

        // 取消按钮
        ItemStack cancel = createItem(Material.LIME_CONCRETE, "<green><!i>取消", "<gray>点击返回编辑界面");
        setAction(cancel, "cancel_delete");
        inv.setItem(15, cancel);

        reopening.add(player.getUniqueId());
        editorSessions.put(player.getUniqueId(), new EditorSession("confirm_delete", 0, tagId));
        player.openInventory(inv);
        reopening.remove(player.getUniqueId());
    }

    private void startCreateTag(Player player) {
        player.closeInventory();
        chatInputSessions.put(player.getUniqueId(), new ChatInputSession(null, "new_tag_id"));
        player.sendMessage(miniMessage.deserialize("<yellow>请在聊天框输入新称号的ID（英文，如 vip_gold）:"));
        player.sendMessage(miniMessage.deserialize("<gray>输入 <red>cancel</red> 取消"));
    }

    private void startEditField(Player player, String tagId, String field) {
        player.closeInventory();
        chatInputSessions.put(player.getUniqueId(), new ChatInputSession(tagId, field));

        String fieldName = switch (field) {
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
            default -> field;
        };

        player.sendMessage(miniMessage.deserialize("<yellow>请在聊天框输入新的 <white>" + fieldName + "</white>:"));
        player.sendMessage(miniMessage.deserialize("<gray>输入 <red>cancel</red> 取消，输入 <yellow>clear</yellow> 清空"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatInputSession inputSession = chatInputSessions.remove(player.getUniqueId());
        if (inputSession == null) return;

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
        // 验证ID格式
        if (!tagId.matches("^[a-zA-Z0-9_]+$")) {
            player.sendMessage(miniMessage.deserialize("<red>无效的称号ID！只能包含字母、数字和下划线"));
            Bukkit.getScheduler().runTask(plugin, () -> openTagListEditor(player, 0));
            return;
        }

        // 检查是否已存在
        if (plugin.getTagManager().getTag(tagId) != null) {
            player.sendMessage(miniMessage.deserialize("<red>称号ID已存在！"));
            Bukkit.getScheduler().runTask(plugin, () -> openTagListEditor(player, 0));
            return;
        }

        // 创建新称号
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

        saveTagConfig();
        plugin.getTagManager().loadTags();

        player.sendMessage(miniMessage.deserialize("<green>称号 <white>" + tagId + "</white> 创建成功！"));
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
                    player.sendMessage(miniMessage.deserialize("<red>无效的材质名称！"));
                    Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
                    return;
                }
                tagsConfig.set(configPath + "material", mat != null ? mat.name() : "PAPER");
            }
            case "item_model" -> tagsConfig.set(configPath + "item-model", value);
            case "tooltip_style" -> tagsConfig.set(configPath + "tooltip-style", value);
            case "custom_model_data" -> {
                try {
                    int cmd = value.isEmpty() ? 0 : Integer.parseInt(value);
                    tagsConfig.set(configPath + "custom-model-data", cmd);
                } catch (NumberFormatException e) {
                    player.sendMessage(miniMessage.deserialize("<red>请输入有效的数字！"));
                    Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
                    return;
                }
            }
        }

        saveTagConfig();
        plugin.getTagManager().loadTags();

        player.sendMessage(miniMessage.deserialize("<green>已更新！"));
        Bukkit.getScheduler().runTask(plugin, () -> openTagEditor(player, tagId));
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
            // 如果正在重新打开菜单或聊天输入，不移除session
            if (reopening.contains(player.getUniqueId()) || chatInputSessions.containsKey(player.getUniqueId())) {
                return;
            }
            editorSessions.remove(player.getUniqueId());
        }
    }

    public boolean isInChatInput(UUID uuid) {
        return chatInputSessions.containsKey(uuid);
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
}
