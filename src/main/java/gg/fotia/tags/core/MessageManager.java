package gg.fotia.tags.core;

import gg.fotia.tags.FotiaTags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final FotiaTags plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration langConfig;
    private String currentLang;

    public MessageManager(FotiaTags plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        this.currentLang = plugin.getConfigManager().getConfig().getString("language", "zh_CN");
        File langFile = new File(plugin.getDataFolder(), "lang/" + currentLang + ".yml");
        if (!langFile.exists()) {
            langFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        }
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public String getRaw(String key) {
        return langConfig.getString(key, key);
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
        return miniMessage.deserialize("<!i>" + text);
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

    public void send(org.bukkit.command.CommandSender sender, String key, String... args) {
        sender.sendMessage(get(key, of(args)));
    }

    public static Map<String, String> of(String... args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            map.put(args[i], args[i + 1]);
        }
        return map;
    }
}
