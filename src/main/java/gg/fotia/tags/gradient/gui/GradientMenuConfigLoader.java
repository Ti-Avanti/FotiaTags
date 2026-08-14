package gg.fotia.tags.gradient.gui;

import gg.fotia.tags.FotiaTags;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GradientMenuConfigLoader {

    private final FotiaTags plugin;

    GradientMenuConfigLoader(FotiaTags plugin) {
        this.plugin = plugin;
    }

    GradientMenuDefinition load(String resourcePath, GradientMenuDefinition fallback) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int size = normalizeSize(config.getInt("size", fallback.size()));
        List<String> layout = config.getStringList("layout");
        if (layout.isEmpty()) {
            layout = fallback.layout();
        }

        Map<Character, GradientMenuItem> items = new HashMap<>(fallback.items());
        ConfigurationSection itemSection = config.getConfigurationSection("items");
        if (itemSection != null) {
            for (String key : itemSection.getKeys(false)) {
                ConfigurationSection section = itemSection.getConfigurationSection(key);
                if (key.length() == 1 && section != null) {
                    items.put(key.charAt(0), loadItem(section));
                }
            }
        }

        List<Integer> slots = cleanSlots(config.getIntegerList("effect-slots"), size);
        if (slots.isEmpty()) {
            slots = cleanSlots(fallback.effectSlots(), size);
        }
        GradientMenuItem selected = loadOptionalItem(config.getConfigurationSection("effect-item.selected"),
                fallback.selectedEffectItem());
        GradientMenuItem unselected = loadOptionalItem(config.getConfigurationSection("effect-item.unselected"),
                fallback.unselectedEffectItem());
        return new GradientMenuDefinition(
                config.getString("title", fallback.title()),
                size,
                List.copyOf(layout),
                Map.copyOf(items),
                List.copyOf(slots),
                selected,
                unselected,
                config.getBoolean("hide-owned", fallback.hideOwned())
        );
    }

    static GradientMenuDefinition storageDefaults() {
        return listDefaults("<!i><gold>动态效果仓库 <gray>(%page%/%total_pages%)", false, "shop", "<aqua><!i>效果商店");
    }

    static GradientMenuDefinition shopDefaults() {
        return listDefaults("<!i><gold>动态效果商店 <gray>(%page%/%total_pages%)", true, "back", "<yellow><!i>返回仓库");
    }

    static GradientMenuDefinition confirmDefaults() {
        Map<Character, GradientMenuItem> items = new HashMap<>();
        items.put('X', item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), ""));
        items.put('Y', item(Material.LIME_CONCRETE, "<green><!i>确认购买", List.of("<gray>价格: %price%", "<yellow>点击确认扣款"), "confirm-buy"));
        items.put('N', item(Material.RED_CONCRETE, "<red><!i>取消", List.of("<gray>返回效果商店"), "cancel"));
        GradientMenuItem effect = effectItem("<white><!i>%effect_name%", List.of(
                "<gray>ID: %effect_id%", "<gray>预览: %effect_preview%", "<gray>有效期: %duration%", "<gray>价格: %price%"
        ), false);
        return new GradientMenuDefinition("<!i><gold>确认购买动态效果", 27,
                List.of("XXXXXXXXX", "XXY E NXX", "XXXXXXXXX"), items, List.of(13), effect, effect, false);
    }

    private static GradientMenuDefinition listDefaults(String title, boolean hideOwned, String centerAction, String centerName) {
        Map<Character, GradientMenuItem> items = new HashMap<>();
        items.put('X', item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), ""));
        items.put('P', item(Material.ARROW, "<yellow><!i>上一页", List.of("<gray>%page%/%total_pages%"), "prev-page"));
        items.put('N', item(Material.ARROW, "<yellow><!i>下一页", List.of("<gray>%page%/%total_pages%"), "next-page"));
        items.put('C', item(Material.BARRIER, "<red><!i>取消动态效果", List.of("<gray>当前: %selected_effect%"), "clear"));
        items.put('S', item(Material.EMERALD, centerName, List.of("<yellow>点击打开"), centerAction));
        GradientMenuItem selected = effectItem("<green><!i>%effect_name% <gray>(使用中)", List.of(
                "<gray>预览: %effect_preview%", "<gray>剩余: %expire%", "", "<yellow>点击取消使用"
        ), true);
        GradientMenuItem unselected = effectItem("<white><!i>%effect_name%", List.of(
                "<gray>预览: %effect_preview%", "<gray>剩余: %expire%", "<gray>价格: %price%", "", "<yellow>点击选择"
        ), false);
        return new GradientMenuDefinition(title, 54,
                List.of("XXXXXXXXX", "X       X", "X       X", "X       X", "X       X", "PXXCSXXNX"),
                items, defaultSlots(), selected, unselected, hideOwned);
    }

    private GradientMenuItem loadOptionalItem(ConfigurationSection section, GradientMenuItem fallback) {
        return section == null ? fallback : loadItem(section);
    }

    private GradientMenuItem loadItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        return new GradientMenuItem(
                material != null ? material : Material.STONE,
                section.getString("name", ""),
                List.copyOf(section.getStringList("lore")),
                section.getString("action", ""),
                section.getString("item-model", section.getString("item_model", "")),
                section.getString("tooltip-style", section.getString("tooltip", "")),
                section.getInt("custom-model-data", 0),
                section.getBoolean("glow", false),
                section.getBoolean("use-effect-icon", false)
        );
    }

    private static GradientMenuItem item(Material material, String name, List<String> lore, String action) {
        return new GradientMenuItem(material, name, lore, action, "", "", 0, false, false);
    }

    private static GradientMenuItem effectItem(String name, List<String> lore, boolean glow) {
        return new GradientMenuItem(Material.NAME_TAG, name, lore, "", "", "", 0, glow, true);
    }

    private List<Integer> cleanSlots(List<Integer> configured, int size) {
        List<Integer> slots = new ArrayList<>();
        for (int slot : configured) {
            if (slot >= 0 && slot < size && !slots.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private int normalizeSize(int size) {
        return Math.max(9, Math.min(54, ((size + 8) / 9) * 9));
    }

    private static List<Integer> defaultSlots() {
        return List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43);
    }
}
