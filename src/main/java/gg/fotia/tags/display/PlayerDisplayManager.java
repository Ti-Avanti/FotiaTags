package gg.fotia.tags.display;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.TextComponentParser;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDisplayManager implements Listener {

    private static final String TEAM_PREFIX = "ft_";
    private static final String DEFAULT_NAME_FORMAT = "<!i>{prefix}{name}{suffix}";
    private static final String DEFAULT_CHAT_FORMAT = "<!i>{prefix}{name}{suffix}<gray>: <white>{message}";

    private final FotiaTags plugin;
    private final Map<UUID, DisplaySnapshot> snapshots = new ConcurrentHashMap<>();
    private DisplaySettings settings = DisplaySettings.disabled();

    public PlayerDisplayManager(FotiaTags plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        this.settings = DisplaySettings.from(plugin.getConfigManager().getConfig());
        if (settings.enabled()) {
            refreshAll();
        } else {
            clearAll();
        }
    }

    public void refreshAll() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::refreshAll);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    public void refreshPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            refreshPlayer(player);
        }
    }

    public void refreshPlayer(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshPlayer(player));
            return;
        }

        if (!settings.enabled()) {
            clearPlayer(player);
            return;
        }

        DisplaySnapshot snapshot = DisplaySnapshot.from(plugin, player);
        snapshots.put(player.getUniqueId(), snapshot);

        applyNameTag(player, snapshot);
        applyTabName(player, snapshot);
    }

    public void clearAll() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::clearAll);
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            clearPlayer(player);
        }
        snapshots.clear();
    }

    public void clearPlayer(Player player) {
        snapshots.remove(player.getUniqueId());
        clearNameTag(player);
        player.playerListName(null);
    }

    public void shutdown() {
        clearAll();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refreshPlayer(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer());
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        DisplaySettings currentSettings = this.settings;
        if (!currentSettings.enabled() || !currentSettings.chatEnabled()) {
            return;
        }

        DisplaySnapshot snapshot = snapshots.getOrDefault(
                event.getPlayer().getUniqueId(),
                DisplaySnapshot.basic(event.getPlayer())
        );
        event.renderer((source, sourceDisplayName, message, viewer) ->
                renderChat(currentSettings, snapshot, message));
    }

    private void applyNameTag(Player player, DisplaySnapshot snapshot) {
        if (!settings.nameTagEnabled() || !settings.directNameTag()) {
            clearNameTag(player);
            return;
        }

        Scoreboard scoreboard = getScoreboard();
        if (scoreboard == null) {
            return;
        }

        Team team = scoreboard.getTeam(getTeamName(player.getUniqueId()));
        if (team == null) {
            team = scoreboard.registerNewTeam(getTeamName(player.getUniqueId()));
        }

        NameTagParts parts = splitNameTagFormat(settings.nameTagFormat());
        team.prefix(parseText(snapshot.apply(parts.prefix())));
        team.suffix(parseText(snapshot.apply(parts.suffix())));
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

        if (!team.hasEntry(player.getName())) {
            removeFromOwnedTeams(scoreboard, player);
            team.addEntry(player.getName());
        }
    }

    private void applyTabName(Player player, DisplaySnapshot snapshot) {
        if (!settings.tabEnabled()) {
            player.playerListName(null);
            return;
        }

        player.playerListName(parseText(snapshot.apply(settings.tabFormat())));
    }

    private Component renderChat(DisplaySettings currentSettings, DisplaySnapshot snapshot, Component message) {
        String format = LegacyColorConverter.convertToMiniMessage(snapshot.apply(currentSettings.chatFormat()));
        if (!format.contains("{message}")) {
            return parseText(format).append(Component.space()).append(message);
        }

        Component result = Component.empty();
        int start = 0;
        int index = format.indexOf("{message}");
        while (index >= 0) {
            result = result.append(parseText(format.substring(start, index))).append(message);
            start = index + "{message}".length();
            index = format.indexOf("{message}", start);
        }
        return result.append(parseText(format.substring(start)));
    }

    private Component parseText(String text) {
        return TextComponentParser.parse(text);
    }

    private NameTagParts splitNameTagFormat(String format) {
        int nameIndex = format.indexOf("{name}");
        int markerLength = "{name}".length();
        int playerIndex = format.indexOf("{player}");
        if (nameIndex == -1 || (playerIndex != -1 && playerIndex < nameIndex)) {
            nameIndex = playerIndex;
            markerLength = "{player}".length();
        }

        if (nameIndex == -1) {
            return new NameTagParts(format, "");
        }
        return new NameTagParts(format.substring(0, nameIndex), format.substring(nameIndex + markerLength));
    }

    private void clearNameTag(Player player) {
        Scoreboard scoreboard = getScoreboard();
        if (scoreboard != null) {
            removeFromOwnedTeams(scoreboard, player);
        }
    }

    private void removeFromOwnedTeams(Scoreboard scoreboard, Player player) {
        for (Team team : new ArrayList<>(scoreboard.getTeams())) {
            if (!team.getName().startsWith(TEAM_PREFIX) || !team.hasEntry(player.getName())) {
                continue;
            }
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }

    private Scoreboard getScoreboard() {
        return Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
    }

    private String getTeamName(UUID uuid) {
        return TEAM_PREFIX + uuid.toString().replace("-", "").substring(0, 13);
    }

    private record DisplaySettings(
            boolean enabled,
            boolean nameTagEnabled,
            boolean directNameTag,
            String nameTagFormat,
            boolean tabEnabled,
            String tabFormat,
            boolean chatEnabled,
            String chatFormat
    ) {
        private static DisplaySettings from(FileConfiguration config) {
            return new DisplaySettings(
                    config.getBoolean("player-display.enabled", false),
                    config.getBoolean("player-display.name-tag.enabled", true),
                    config.getBoolean("player-display.name-tag.direct", true),
                    config.getString("player-display.name-tag.format", DEFAULT_NAME_FORMAT),
                    config.getBoolean("player-display.tab.enabled", true),
                    config.getString("player-display.tab.format", DEFAULT_NAME_FORMAT),
                    config.getBoolean("player-display.chat.enabled", true),
                    config.getString("player-display.chat.format", DEFAULT_CHAT_FORMAT)
            );
        }

        private static DisplaySettings disabled() {
            return new DisplaySettings(false, false, false, DEFAULT_NAME_FORMAT, false, DEFAULT_NAME_FORMAT, false, DEFAULT_CHAT_FORMAT);
        }
    }

    private record DisplaySnapshot(
            String name,
            String prefix,
            String suffix,
            String prefix2,
            String suffix2,
            String tag,
            String tagName
    ) {
        private static DisplaySnapshot from(FotiaTags plugin, Player player) {
            UUID uuid = player.getUniqueId();
            return new DisplaySnapshot(
                    player.getName(),
                    plugin.getTagManager().getCurrentPrefix(uuid),
                    plugin.getTagManager().getCurrentSuffix(uuid),
                    plugin.getTagManager().getCurrentPrefix2(uuid),
                    plugin.getTagManager().getCurrentSuffix2(uuid),
                    plugin.getTagManager().getCurrentTagId(uuid),
                    plugin.getTagManager().getCurrentTagName(uuid)
            );
        }

        private static DisplaySnapshot basic(Player player) {
            return new DisplaySnapshot(player.getName(), "", "", "", "", "", "");
        }

        private String apply(String format) {
            if (format == null || format.isEmpty()) {
                return "";
            }

            return format
                    .replace("{name}", name)
                    .replace("{player}", name)
                    .replace("{prefix}", prefix)
                    .replace("{suffix}", suffix)
                    .replace("{prefix2}", prefix2)
                    .replace("{suffix2}", suffix2)
                    .replace("{tag}", tag)
                    .replace("{tag_name}", tagName);
        }
    }

    private record NameTagParts(String prefix, String suffix) {
    }
}
