package gg.fotia.tags;

import gg.fotia.tags.command.TagCommand;
import gg.fotia.tags.core.ConfigManager;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.gui.MenuManager;
import gg.fotia.tags.gui.TagEditorManager;
import gg.fotia.tags.hook.PlaceholderAPIHook;
import gg.fotia.tags.storage.DatabaseManager;
import gg.fotia.tags.storage.MySQLManager;
import gg.fotia.tags.storage.SQLiteManager;
import gg.fotia.tags.tag.TagManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FotiaTags extends JavaPlugin {

    private static FotiaTags instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private TagManager tagManager;
    private MenuManager menuManager;
    private TagEditorManager tagEditorManager;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化配置管理器
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigs();

        // 初始化消息管理器
        this.messageManager = new MessageManager(this);

        // 初始化数据库
        initDatabase();

        // 初始化称号管理器
        this.tagManager = new TagManager(this);
        this.tagManager.loadTags();

        // 初始化菜单管理器
        this.menuManager = new MenuManager(this);
        this.menuManager.loadMenus();

        // 初始化称号编辑器
        this.tagEditorManager = new TagEditorManager(this);

        // 注册命令
        TagCommand tagCommand = new TagCommand(this);
        getCommand("fotiatags").setExecutor(tagCommand);
        getCommand("fotiatags").setTabCompleter(tagCommand);

        // 注册PlaceholderAPI扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this).register();
            getLogger().info("PlaceholderAPI hook registered!");
        }

        getLogger().info("FotiaTags has been enabled!");
    }

    @Override
    public void onDisable() {
        // 保存所有玩家数据
        if (tagManager != null) {
            tagManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("FotiaTags has been disabled!");
    }

    private void initDatabase() {
        String type = configManager.getConfig().getString("database.type", "sqlite").toLowerCase();

        if (type.equals("mysql")) {
            String host = configManager.getConfig().getString("database.mysql.host", "localhost");
            int port = configManager.getConfig().getInt("database.mysql.port", 3306);
            String database = configManager.getConfig().getString("database.mysql.database", "fotiatags");
            String username = configManager.getConfig().getString("database.mysql.username", "root");
            String password = configManager.getConfig().getString("database.mysql.password", "");

            this.databaseManager = new MySQLManager(this, host, port, database, username, password);
        } else {
            this.databaseManager = new SQLiteManager(this);
        }

        this.databaseManager.initialize();
    }

    public void reload() {
        configManager.loadConfigs();
        messageManager.reload();
        tagManager.loadTags();
        menuManager.loadMenus();
    }

    public static FotiaTags getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public TagManager getTagManager() {
        return tagManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public TagEditorManager getTagEditorManager() {
        return tagEditorManager;
    }
}
