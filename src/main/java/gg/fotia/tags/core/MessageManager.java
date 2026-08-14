package gg.fotia.tags.core;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.util.TextComponentParser;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageManager {

    private final FotiaTags plugin;
    private FileConfiguration langConfig;
    private FileConfiguration bundledLangConfig;
    private FileConfiguration bundledZhConfig;
    private String currentLang;

    public MessageManager(FotiaTags plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.currentLang = plugin.getConfigManager().getConfig().getString("settings.language", "zh_CN");
        File langFile = new File(plugin.getDataFolder(), "lang/" + currentLang + ".yml");
        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        }
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
        this.bundledLangConfig = loadBundledLanguage(currentLang);
        this.bundledZhConfig = currentLang.equalsIgnoreCase("zh_CN") ? bundledLangConfig : loadBundledLanguage("zh_CN");
    }

    public String getRaw(String key) {
        return getRaw(key, key);
    }

    public String getRaw(String key, String defaultValue) {
        String message = langConfig.getString(key);
        if (message != null) {
            return message;
        }

        message = bundledLangConfig.getString(key);
        if (message != null) {
            return message;
        }

        message = bundledZhConfig.getString(key);
        return message != null ? message : defaultValue;
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        String message = getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public Component get(String key) {
        return parse(getRaw(key));
    }

    public Component get(String key, Map<String, String> placeholders) {
        return parse(getRaw(key, placeholders));
    }

    public Component parse(String text) {
        return TextComponentParser.parse(text);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(get(key, placeholders));
    }

    public void send(Player player, String key, String... args) {
        player.sendMessage(get(key, of(args)));
    }

    public void send(org.bukkit.command.CommandSender sender, String key) {
        sender.sendMessage(get(key));
    }

    public void send(org.bukkit.command.CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(get(key, placeholders));
    }

    public void send(org.bukkit.command.CommandSender sender, String key, String... args) {
        sender.sendMessage(get(key, of(args)));
    }

    public String formatDuration(long millis) {
        if (millis == -1) {
            return getDurationText("duration-permanent", "永久", "Permanent");
        }

        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        List<String> parts = new ArrayList<>();
        if (days > 0) {
            addDurationPart(parts, days, getDurationText("duration-day", "天", "d"));
            addDurationPart(parts, hours % 24, getDurationText("duration-hour", "小时", "h"));
        } else if (hours > 0) {
            addDurationPart(parts, hours, getDurationText("duration-hour", "小时", "h"));
            addDurationPart(parts, minutes % 60, getDurationText("duration-minute", "分钟", "m"));
        } else if (minutes > 0) {
            addDurationPart(parts, minutes, getDurationText("duration-minute", "分钟", "m"));
            addDurationPart(parts, seconds % 60, getDurationText("duration-second", "秒", "s"));
        } else {
            addDurationPart(parts, seconds, getDurationText("duration-second", "秒", "s"));
        }

        return String.join(getDurationText("duration-separator", "", " "), parts);
    }

    public String formatExpireTime(long expireTime) {
        if (expireTime == -1) {
            return getDurationText("duration-permanent", "永久", "Permanent");
        }

        long remaining = expireTime - System.currentTimeMillis();
        if (remaining <= 0) {
            return getDurationText("duration-expired", "已过期", "Expired");
        }

        return formatDuration(remaining);
    }

    private void addDurationPart(List<String> parts, long value, String unit) {
        if (value > 0 || parts.isEmpty()) {
            parts.add(value + unit);
        }
    }

    private String getDurationText(String key, String zhFallback, String enFallback) {
        String fallback = currentLang != null && currentLang.toLowerCase().startsWith("zh") ? zhFallback : enFallback;
        return getRaw(key, fallback);
    }

    private FileConfiguration loadBundledLanguage(String language) {
        String resourcePath = "lang/" + language + ".yml";
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return new YamlConfiguration();
        }
    }

    public static Map<String, String> of(String... args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            map.put(args[i], args[i + 1]);
        }
        return map;
    }
}
