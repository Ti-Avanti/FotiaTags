package gg.fotia.tags.core;

import gg.fotia.tags.FotiaTags;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final FotiaTags plugin;
    private FileConfiguration config;
    private FileConfiguration tagsConfig;

    public ConfigManager(FotiaTags plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // 加载主配置
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 加载称号配置
        this.tagsConfig = loadConfig("tags.yml");

        // 确保语言文件夹存在
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // 确保菜单文件夹存在
        File menusFolder = new File(plugin.getDataFolder(), "menus");
        if (!menusFolder.exists()) {
            menusFolder.mkdirs();
        }

        // 保存默认语言文件
        saveDefaultResource("lang/zh_CN.yml");
        saveDefaultResource("lang/en_US.yml");

        // 保存默认菜单配置
        saveDefaultResource("menus/tag-select.yml");
    }

    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveDefaultResource(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    public void saveTagsConfig() {
        try {
            File file = new File(plugin.getDataFolder(), "tags.yml");
            tagsConfig.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save tags.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getTagsConfig() {
        return tagsConfig;
    }

    public void reloadTagsConfig() {
        this.tagsConfig = loadConfig("tags.yml");
    }
}
