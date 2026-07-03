package gg.fotia.tags.command;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.storage.PlayerProfile;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class TagCommand implements CommandExecutor, TabCompleter {

    private final FotiaTags plugin;

    public TagCommand(FotiaTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (player.hasPermission("fotiatags.use")) {
                    plugin.getMenuManager().openTagSelectMenu(player);
                    return true;
                }
            }
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "set" -> handleSet(sender, args);
            case "list" -> handleList(sender, args);
            case "menu" -> handleMenu(sender);
            case "custom" -> handleCustom(sender);
            case "reload" -> handleReload(sender);
            case "export" -> handleExport(sender);
            case "import" -> handleImport(sender, args);
            case "editor" -> handleEditor(sender);
            case "player" -> handlePlayer(sender, args);
            case "players" -> handlePlayers(sender);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fotiatags.admin.give")) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "no-permission");
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().get("help-give"));
            return;
        }

        String playerName = args[1];
        String tagId = args[2];
        String durationStr = args.length > 3 ? args[3] : "permanent";

        // 检查称号是否存在
        Tag tag = plugin.getTagManager().getTag(tagId);
        if (tag == null) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "tag-not-found",
                        MessageManager.of("tag", tagId));
            } else {
                sender.sendMessage("Tag not found: " + tagId);
            }
            return;
        }

        // 解析时长
        long duration = TimeUtil.parseDuration(durationStr);
        if (duration == Long.MIN_VALUE || (duration != -1 && duration > Long.MAX_VALUE - System.currentTimeMillis())) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "invalid-duration",
                        MessageManager.of("duration", durationStr));
            } else {
                sender.sendMessage("Invalid duration: " + durationStr);
            }
            return;
        }

        // 查找玩家
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "player-not-found",
                        MessageManager.of("player", playerName));
            } else {
                sender.sendMessage("Player not found: " + playerName);
            }
            return;
        }
        plugin.getDatabaseManager().savePlayerProfile(target.getUniqueId(), target.getName() != null ? target.getName() : playerName);

        // 给予称号
        plugin.getTagManager().giveTag(target.getUniqueId(), tagId, duration);

        String durationDisplay = plugin.getMessageManager().formatDuration(duration);
        if (sender instanceof Player p) {
            plugin.getMessageManager().send(p, "tag-given",
                    MessageManager.of("player", playerName, "tag", tag.getDisplayName(), "duration", durationDisplay));
        } else {
            sender.sendMessage("Gave " + playerName + " tag " + tag.getDisplayName() + " for " + durationDisplay);
        }

        // 通知目标玩家
        if (target.isOnline()) {
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null) {
                plugin.getMessageManager().send(targetPlayer, "tag-received",
                        MessageManager.of("tag", tag.getDisplayName(), "duration", durationDisplay));
            }
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fotiatags.admin.remove")) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "no-permission");
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().get("help-remove"));
            return;
        }

        String playerName = args[1];
        String tagId = args[2];

        // 查找玩家
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "player-not-found",
                        MessageManager.of("player", playerName));
            }
            return;
        }
        plugin.getDatabaseManager().savePlayerProfile(target.getUniqueId(), target.getName() != null ? target.getName() : playerName);

        Tag tag = plugin.getTagManager().getTag(tagId);
        String tagName = tag != null ? tag.getDisplayName() : tagId;

        // 移除称号
        plugin.getTagManager().removeTag(target.getUniqueId(), tagId);

        if (sender instanceof Player p) {
            plugin.getMessageManager().send(p, "tag-removed",
                    MessageManager.of("player", playerName, "tag", tagName));
        }

        // 通知目标玩家
        if (target.isOnline()) {
            Player targetPlayer = target.getPlayer();
            if (targetPlayer != null) {
                plugin.getMessageManager().send(targetPlayer, "tag-lost",
                        MessageManager.of("tag", tagName));
            }
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fotiatags.admin.set")) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "no-permission");
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().get("help-set"));
            return;
        }

        String playerName = args[1];
        String tagId = args[2];

        // 查找玩家
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "player-not-online",
                        MessageManager.of("player", playerName));
            }
            return;
        }

        // 检查是否清除称号
        if (tagId.equalsIgnoreCase("none") || tagId.equalsIgnoreCase("clear")) {
            plugin.getTagManager().setCurrentTag(target.getUniqueId(), null);
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "tag-cleared",
                        MessageManager.of("player", playerName));
            }
            plugin.getMessageManager().send(target, "your-tag-cleared");
            return;
        }

        Tag tag = plugin.getTagManager().getTag(tagId);
        if (tag == null) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "tag-not-found",
                        MessageManager.of("tag", tagId));
            }
            return;
        }

        // 设置称号
        plugin.getTagManager().setCurrentTag(target.getUniqueId(), tagId);

        if (sender instanceof Player p) {
            plugin.getMessageManager().send(p, "tag-set",
                    MessageManager.of("player", playerName, "tag", tag.getDisplayName()));
        }

        plugin.getMessageManager().send(target, "your-tag-set",
                MessageManager.of("tag", tag.getDisplayName()));
    }

    private void handleList(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 1) {
            if (!sender.hasPermission("fotiatags.admin.list")) {
                if (sender instanceof Player p) {
                    plugin.getMessageManager().send(p, "no-permission");
                }
                return;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                if (sender instanceof Player p) {
                    plugin.getMessageManager().send(p, "player-not-online",
                            MessageManager.of("player", args[1]));
                }
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Usage: /fotiatags list <player>");
                return;
            }
            target = (Player) sender;
        }

        PlayerTagData data = plugin.getTagManager().getPlayerData(target.getUniqueId());
        if (data == null) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "tag-list-empty");
            }
            return;
        }

        List<String> ownedTags = data.getValidTags();
        if (sender instanceof Player p) {
            plugin.getMessageManager().send(p, "tag-list-header");

            if (ownedTags.isEmpty()) {
                plugin.getMessageManager().send(p, "tag-list-empty");
            } else {
                for (String tagId : ownedTags) {
                    Tag tag = plugin.getTagManager().getTag(tagId);
                    String tagName = tag != null ? tag.getDisplayName() : tagId;
                    long expireTime = data.getTagExpireTime(tagId);
                    String expireStr = plugin.getMessageManager().formatExpireTime(expireTime);

                    plugin.getMessageManager().send(p, "tag-list-item",
                            MessageManager.of("tag", tagName, "expire", expireStr));
                }
            }

            plugin.getMessageManager().send(p, "tag-list-footer",
                    MessageManager.of("count", String.valueOf(ownedTags.size())));
        }
    }

    private void handleMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return;
        }

        if (!player.hasPermission("fotiatags.use")) {
            plugin.getMessageManager().send(player, "no-permission");
            return;
        }

        plugin.getMenuManager().openTagSelectMenu(player);
    }

    private void handleCustom(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return;
        }

        if (!player.hasPermission("fotiatags.custom")) {
            plugin.getMessageManager().send(player, "no-permission");
            return;
        }

        plugin.getCustomTagManager().openCustomMenu(player);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("fotiatags.admin.reload")) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "no-permission");
            }
            return;
        }

        plugin.reload();

        if (sender instanceof Player p) {
            plugin.getMessageManager().send(p, "config-reloaded");
        } else {
            sender.sendMessage("Configuration reloaded!");
        }
    }

    private void handleEditor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return;
        }

        if (!player.hasPermission("fotiatags.admin.editor")) {
            plugin.getMessageManager().send(player, "no-permission");
            return;
        }

        plugin.getTagEditorManager().openTagListEditor(player, 0);
    }

    private void handlePlayers(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players!");
            return;
        }

        if (!player.hasPermission("fotiatags.admin.players")) {
            plugin.getMessageManager().send(player, "no-permission");
            return;
        }

        plugin.getPlayerTagAdminManager().openPlayerList(player);
    }

    private void handlePlayer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage("This command can only be used by players!");
            return;
        }

        if (!admin.hasPermission("fotiatags.admin.players")) {
            plugin.getMessageManager().send(admin, "no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().send(admin, "help-player");
            return;
        }

        String input = args[1];
        Player onlineTarget = Bukkit.getPlayerExact(input);
        if (onlineTarget != null) {
            openPlayerAdminMenu(admin, onlineTarget.getUniqueId(), onlineTarget.getName());
            return;
        }

        UUID uuid = parseUuid(input);
        if (uuid != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            if (target.hasPlayedBefore() || target.getName() != null) {
                openPlayerAdminMenu(admin, uuid, target.getName() != null ? target.getName() : input);
                return;
            }
        } else {
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            if (target.hasPlayedBefore()) {
                openPlayerAdminMenu(admin, target.getUniqueId(), target.getName() != null ? target.getName() : input);
                return;
            }
        }

        plugin.getDatabaseManager().loadPlayerProfiles()
                .whenComplete((profiles, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getMessageManager().send(admin, "admin-player-list-load-failed");
                        return;
                    }

                    PlayerProfile profile = findProfile(profiles, input, uuid);
                    if (profile == null) {
                        plugin.getMessageManager().send(admin, "player-not-found",
                                MessageManager.of("player", input));
                        return;
                    }

                    openPlayerAdminMenu(admin, profile.uuid(), profile.displayName());
                }));
    }

    private void openPlayerAdminMenu(Player admin, UUID targetUuid, String targetName) {
        plugin.getDatabaseManager().savePlayerProfile(targetUuid, targetName);
        plugin.getPlayerTagAdminManager().openPlayerTags(admin, targetUuid, targetName);
    }

    private UUID parseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PlayerProfile findProfile(List<PlayerProfile> profiles, String input, UUID uuid) {
        String normalizedInput = input.toLowerCase(Locale.ROOT);
        for (PlayerProfile profile : profiles) {
            if (uuid != null && profile.uuid().equals(uuid)) {
                return profile;
            }
            if (profile.displayName().toLowerCase(Locale.ROOT).equals(normalizedInput)) {
                return profile;
            }
        }
        return null;
    }

    private void handleExport(CommandSender sender) {
        if (!sender.hasPermission("fotiatags.admin.export")) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "no-permission");
            }
            return;
        }

        // 创建导出文件夹
        File exportFolder = new File(plugin.getDataFolder(), "export");
        if (!exportFolder.exists()) {
            exportFolder.mkdirs();
        }

        // 生成文件名（带时间戳）
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File exportFile = new File(exportFolder, "tags_export_" + timestamp + ".yml");

        // 复制tags配置
        YamlConfiguration exportConfig = new YamlConfiguration();
        ConfigurationSection tagsSection = plugin.getConfigManager().getTagsConfig().getConfigurationSection("tags");
        if (tagsSection != null) {
            for (String tagId : tagsSection.getKeys(false)) {
                ConfigurationSection tagSection = tagsSection.getConfigurationSection(tagId);
                if (tagSection != null) {
                    for (String key : tagSection.getKeys(false)) {
                        exportConfig.set("tags." + tagId + "." + key, tagSection.get(key));
                    }
                }
            }
        }

        // 复制default-tag
        String defaultTag = plugin.getConfigManager().getTagsConfig().getString("default-tag", "");
        exportConfig.set("default-tag", defaultTag);

        try {
            exportConfig.save(exportFile);
            int tagCount = tagsSection != null ? tagsSection.getKeys(false).size() : 0;
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "export-success",
                        MessageManager.of("count", String.valueOf(tagCount), "file", exportFile.getName()));
            } else {
                sender.sendMessage("Exported " + tagCount + " tags to " + exportFile.getName());
            }
        } catch (IOException e) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "export-failed",
                        MessageManager.of("error", e.getMessage()));
            } else {
                sender.sendMessage("Export failed: " + e.getMessage());
            }
        }
    }

    private void handleImport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fotiatags.admin.import")) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "no-permission");
            }
            return;
        }

        if (args.length < 2) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "help-import");
            } else {
                sender.sendMessage("Usage: /fotiatags import <filename> [replace]");
            }
            return;
        }

        String fileName = args[1];
        if (!fileName.endsWith(".yml")) {
            fileName += ".yml";
        }

        // 检查是否为完全替换模式
        boolean replaceMode = args.length > 2 && args[2].equalsIgnoreCase("replace");

        // 先在export文件夹找，再在插件根目录找
        File importFile = new File(plugin.getDataFolder(), "export/" + fileName);
        if (!importFile.exists()) {
            importFile = new File(plugin.getDataFolder(), fileName);
        }

        if (!importFile.exists()) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "import-file-not-found",
                        MessageManager.of("file", fileName));
            } else {
                sender.sendMessage("Import file not found: " + fileName);
            }
            return;
        }

        YamlConfiguration importConfig = YamlConfiguration.loadConfiguration(importFile);
        ConfigurationSection importTags = importConfig.getConfigurationSection("tags");

        if (importTags == null || importTags.getKeys(false).isEmpty()) {
            if (sender instanceof Player p) {
                plugin.getMessageManager().send(p, "import-empty");
            } else {
                sender.sendMessage("No tags found in import file!");
            }
            return;
        }

        // 获取当前tags配置
        YamlConfiguration tagsConfig = (YamlConfiguration) plugin.getConfigManager().getTagsConfig();
        int importedCount = 0;
        int overwrittenCount = 0;
        int deletedCount = 0;

        // 如果是替换模式，先删除不在导入文件中的称号
        if (replaceMode) {
            ConfigurationSection currentTags = tagsConfig.getConfigurationSection("tags");
            if (currentTags != null) {
                Set<String> importTagIds = importTags.getKeys(false);
                for (String existingTagId : new HashSet<>(currentTags.getKeys(false))) {
                    if (!importTagIds.contains(existingTagId)) {
                        tagsConfig.set("tags." + existingTagId, null);
                        deletedCount++;
                    }
                }
            }
        }

        for (String tagId : importTags.getKeys(false)) {
            ConfigurationSection tagSection = importTags.getConfigurationSection(tagId);
            if (tagSection == null) continue;

            // 检查是否已存在（覆盖）
            if (tagsConfig.contains("tags." + tagId)) {
                overwrittenCount++;
            }

            // 写入配置
            for (String key : tagSection.getKeys(false)) {
                tagsConfig.set("tags." + tagId + "." + key, tagSection.get(key));
            }
            importedCount++;
        }

        // 导入default-tag配置
        if (importConfig.contains("default-tag")) {
            tagsConfig.set("default-tag", importConfig.getString("default-tag"));
        }

        // 保存配置
        plugin.getConfigManager().saveTagsConfig();

        // 重载称号
        plugin.getTagManager().loadTags();

        if (sender instanceof Player p) {
            if (replaceMode) {
                plugin.getMessageManager().send(p, "import-replace-success",
                        MessageManager.of("count", String.valueOf(importedCount), "overwritten", String.valueOf(overwrittenCount), "deleted", String.valueOf(deletedCount)));
            } else {
                plugin.getMessageManager().send(p, "import-success",
                        MessageManager.of("count", String.valueOf(importedCount), "overwritten", String.valueOf(overwrittenCount)));
            }
        } else {
            if (replaceMode) {
                sender.sendMessage("Imported " + importedCount + " tags (" + overwrittenCount + " overwritten, " + deletedCount + " deleted)");
            } else {
                sender.sendMessage("Imported " + importedCount + " tags (" + overwrittenCount + " overwritten)");
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        if (sender instanceof Player p) {
            plugin.getMessageManager().send(p, "help-header");
            if (p.hasPermission("fotiatags.admin.give")) {
                plugin.getMessageManager().send(p, "help-give");
            }
            if (p.hasPermission("fotiatags.admin.remove")) {
                plugin.getMessageManager().send(p, "help-remove");
            }
            if (p.hasPermission("fotiatags.admin.set")) {
                plugin.getMessageManager().send(p, "help-set");
            }
            plugin.getMessageManager().send(p, "help-list");
            plugin.getMessageManager().send(p, "help-menu");
            if (p.hasPermission("fotiatags.custom")) {
                plugin.getMessageManager().send(p, "help-custom");
            }
            if (p.hasPermission("fotiatags.admin.reload")) {
                plugin.getMessageManager().send(p, "help-reload");
            }
            if (p.hasPermission("fotiatags.admin.editor")) {
                plugin.getMessageManager().send(p, "help-editor");
            }
            if (p.hasPermission("fotiatags.admin.players")) {
                plugin.getMessageManager().send(p, "help-players");
                plugin.getMessageManager().send(p, "help-player");
            }
            if (p.hasPermission("fotiatags.admin.export")) {
                plugin.getMessageManager().send(p, "help-export");
            }
            if (p.hasPermission("fotiatags.admin.import")) {
                plugin.getMessageManager().send(p, "help-import");
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("menu");
            subCommands.add("list");
            if (sender.hasPermission("fotiatags.custom")) subCommands.add("custom");
            if (sender.hasPermission("fotiatags.admin.give")) subCommands.add("give");
            if (sender.hasPermission("fotiatags.admin.remove")) subCommands.add("remove");
            if (sender.hasPermission("fotiatags.admin.set")) subCommands.add("set");
            if (sender.hasPermission("fotiatags.admin.reload")) subCommands.add("reload");
            if (sender.hasPermission("fotiatags.admin.editor")) subCommands.add("editor");
            if (sender.hasPermission("fotiatags.admin.players")) {
                subCommands.add("players");
                subCommands.add("player");
            }
            if (sender.hasPermission("fotiatags.admin.export")) subCommands.add("export");
            if (sender.hasPermission("fotiatags.admin.import")) subCommands.add("import");
            subCommands.add("help");

            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") || subCommand.equals("remove") || subCommand.equals("set") || subCommand.equals("list") || subCommand.equals("player")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (subCommand.equals("import")) {
                // 列出export文件夹中的yml文件
                File exportFolder = new File(plugin.getDataFolder(), "export");
                if (exportFolder.exists() && exportFolder.isDirectory()) {
                    File[] files = exportFolder.listFiles((dir, name) -> name.endsWith(".yml"));
                    if (files != null) {
                        return Arrays.stream(files)
                                .map(File::getName)
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                }
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give") || subCommand.equals("remove") || subCommand.equals("set")) {
                if (subCommand.equals("set")) {
                    completions.add("none");
                }
                completions.addAll(plugin.getTagManager().getTags().keySet());
                return completions.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (subCommand.equals("import")) {
                return Collections.singletonList("replace").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("give")) {
                return Arrays.asList("permanent", "1d", "7d", "30d", "1h", "30m").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}
