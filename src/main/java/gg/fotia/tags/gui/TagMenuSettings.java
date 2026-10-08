package gg.fotia.tags.gui;

import gg.fotia.tags.FotiaTags;
import java.io.File;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 在启动或重载时读取显示配置，绘制期间不访问文件。 */
public record TagMenuSettings(Set<String> enabledViews, boolean compactDefault, Profile standard, Profile compact,
                              String inputMenu, String fallbackIcon, Map<String,String> icons) {
    public static final Set<String> VIEWS = Set.of("tag-select", "custom-tag", "custom-tag-icons",
            "custom-tag-detail", "custom-tag-delete", "gradient-storage", "gradient-shop", "gradient-purchase-confirm");
    public record Profile(String menu, int pageSize) {}
    public Profile profile(boolean small) { return small ? compact : standard; }
    public static TagMenuSettings load(FotiaTags plugin) {
        File file = new File(plugin.getDataFolder(), "futureui.yml");
        if (!file.exists()) plugin.saveResource("futureui.yml", false);
        var config = YamlConfiguration.loadConfiguration(file);
        Set<String> views = new HashSet<>();
        for (String view : VIEWS) {
            File menu = new File(plugin.getDataFolder(), "menus/" + view + ".yml");
            if (!menu.exists()) plugin.saveResource("menus/" + view + ".yml", false);
            String engine = YamlConfiguration.loadConfiguration(menu).getString("ui-engine", "inventory");
            if (!Set.of("inventory", "futureui").contains(engine))
                throw new IllegalArgumentException(menu.getName() + " ui-engine: inventory / futureui");
            if (engine.equals("futureui")) views.add(view);
        }
        String layout = config.getString("default-layout", "standard");
        if (!Set.of("standard", "compact").contains(layout)) throw new IllegalArgumentException("default-layout: standard / compact");
        Map<String,String> icons = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("icons.entries");
        if (section != null) section.getValues(false).forEach((key,value) -> icons.put(key, String.valueOf(value)));
        return new TagMenuSettings(Set.copyOf(views), layout.equals("compact"), profile(config, "standard", 4),
                profile(config, "compact", 2), config.getString("input-menu", "fotiatags/input"),
                config.getString("icons.fallback", "paper"), Map.copyOf(icons));
    }
    private static Profile profile(ConfigurationSection config, String name, int fallback) {
        int size = config.getInt("layouts." + name + ".page-size", fallback);
        if (size < 1 || size > 36) throw new IllegalArgumentException("layouts." + name + ".page-size: 1..36");
        String menu = config.getString("layouts." + name + ".menu", "fotiatags/" + name);
        if (menu.isBlank()) throw new IllegalArgumentException("FutureUI menu must not be blank");
        return new Profile(menu, size);
    }
}
