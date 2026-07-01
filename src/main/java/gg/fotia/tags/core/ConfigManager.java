package gg.fotia.tags.core;

import gg.fotia.tags.FotiaTags;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

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
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
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

        // 确保粒子模板文件夹存在
        File particlesFolder = new File(plugin.getDataFolder(), "particles");
        if (!particlesFolder.exists()) {
            particlesFolder.mkdirs();
        }

        // 保存默认语言文件
        saveDefaultResource("lang/zh_CN.yml");
        saveDefaultResource("lang/en_US.yml");

        // 保存默认菜单配置
        saveDefaultResource("menus/tag-select.yml");
        saveDefaultResource("menus/custom-tag.yml");
        saveDefaultResource("menus/custom-tag-icons.yml");
        saveDefaultResource("menus/custom-tag-detail.yml");
        saveDefaultResource("menus/custom-tag-delete.yml");
        saveDefaultResource("menus/tag-editor-list.yml");
        saveDefaultResource("menus/tag-editor-edit.yml");
        saveDefaultResource("menus/tag-editor-delete.yml");
        saveDefaultResource("menus/tag-editor-particles.yml");
        saveDefaultResource("menus/player-manager-list.yml");
        saveDefaultResource("menus/player-manager-tags.yml");

        // 保存默认粒子模板
        saveDefaultResource("particles/halo.yml");
        saveDefaultResource("particles/trail.yml");
        saveDefaultResource("particles/feet.yml");
        saveDefaultResource("particles/burst.yml");
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
