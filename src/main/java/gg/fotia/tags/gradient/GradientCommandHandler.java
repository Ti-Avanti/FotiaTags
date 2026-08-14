package gg.fotia.tags.gradient;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

public class GradientCommandHandler {

    private final FotiaTags plugin;

    public GradientCommandHandler(FotiaTags plugin) {
        this.plugin = plugin;
    }

    public void handleMenus(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("fotiatags.effects")) {
            plugin.getMessageManager().send(player, "no-permission");
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("shop")) {
            plugin.getGradientMenuManager().openShop(player);
        } else {
            plugin.getGradientMenuManager().openStorage(player);
        }
    }

    public void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("fotiatags.admin.effect")) {
            plugin.getMessageManager().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "gradient-admin-help");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "remove" -> remove(sender, args);
            case "set" -> set(sender, args);
            case "list" -> list(sender, args);
            default -> plugin.getMessageManager().send(sender, "gradient-admin-help");
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("effects")) {
            return filter(List.of("shop"), args[1]);
        }
        if (!args[0].equalsIgnoreCase("effect") || !sender.hasPermission("fotiatags.admin.effect")) {
            return null;
        }
        if (args.length == 2) {
            return filter(List.of("give", "remove", "set", "list"), args[1]);
        }
        if (args.length == 3) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 4 && Stream.of("give", "remove", "set").anyMatch(args[1]::equalsIgnoreCase)) {
            List<String> ids = new ArrayList<>();
            if (args[1].equalsIgnoreCase("set")) {
                ids.add("none");
            }
            plugin.getGradientManager().getEffects().stream().map(GradientEffect::id).forEach(ids::add);
            return filter(ids, args[3]);
        }
        if (args.length == 5 && args[1].equalsIgnoreCase("give")) {
            return filter(List.of("permanent", "1h", "1d", "7d", "30d"), args[4]);
        }
        return List.of();
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getMessageManager().send(sender, "gradient-admin-give-usage");
            return;
        }
        GradientEffect effect = plugin.getGradientManager().getEffect(args[3]);
        if (effect == null || !effect.enabled()) {
            sendNotFound(sender, args[3]);
            return;
        }
        long duration = TimeUtil.parseDuration(args.length > 4 ? args[4] : "permanent");
        if (duration == Long.MIN_VALUE || duration != -1L && duration > Long.MAX_VALUE - System.currentTimeMillis()) {
            plugin.getMessageManager().send(sender, "invalid-duration", "duration", args.length > 4 ? args[4] : "permanent");
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        plugin.getDatabaseManager().savePlayerProfile(target.getUniqueId(), target.getName() != null ? target.getName() : args[2]);
        plugin.getGradientManager().grantEffect(target.getUniqueId(), effect.id(), duration)
                .whenComplete((ignored, error) -> runSync(() -> {
                    if (error != null) {
                        plugin.getMessageManager().send(sender, "gradient-operation-failed");
                        return;
                    }
                    plugin.getMessageManager().send(sender, "gradient-admin-given", MessageManager.of(
                            "player", args[2], "effect", effect.displayName(),
                            "duration", plugin.getMessageManager().formatDuration(duration)));
                    if (target.getPlayer() != null) {
                        plugin.getMessageManager().send(target.getPlayer(), "gradient-received", MessageManager.of(
                                "effect", effect.displayName(), "duration", plugin.getMessageManager().formatDuration(duration)));
                    }
                }));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getMessageManager().send(sender, "gradient-admin-remove-usage");
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        GradientEffect effect = plugin.getGradientManager().getEffect(args[3]);
        String displayName = effect != null ? effect.displayName() : args[3];
        plugin.getGradientManager().removeEffect(target.getUniqueId(), args[3])
                .whenComplete((ignored, error) -> runSync(() -> plugin.getMessageManager().send(sender,
                        error == null ? "gradient-admin-removed" : "gradient-operation-failed",
                        MessageManager.of("player", args[2], "effect", displayName))));
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getMessageManager().send(sender, "gradient-admin-set-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.getMessageManager().send(sender, "player-not-online", "player", args[2]);
            return;
        }
        if (args[3].equalsIgnoreCase("none") || args[3].equalsIgnoreCase("clear")) {
            plugin.getGradientManager().clearSelection(target.getUniqueId())
                    .whenComplete((ignored, error) -> runSync(() -> plugin.getMessageManager().send(sender,
                            error == null ? "gradient-admin-cleared" : "gradient-operation-failed", "player", target.getName())));
            return;
        }
        GradientEffect effect = plugin.getGradientManager().getEffect(args[3]);
        if (effect == null || !effect.enabled()) {
            sendNotFound(sender, args[3]);
            return;
        }
        plugin.getGradientManager().selectEffect(target.getUniqueId(), effect.id())
                .whenComplete((selected, error) -> runSync(() -> plugin.getMessageManager().send(sender,
                        error == null && Boolean.TRUE.equals(selected) ? "gradient-admin-set" : "gradient-not-owned",
                        MessageManager.of("player", target.getName(), "effect", effect.displayName()))));
    }

    private void list(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "gradient-admin-list-usage");
            return;
        }
        OfflinePlayer target = findPlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        UUID uuid = target.getUniqueId();
        plugin.getDatabaseManager().loadPlayerData(uuid).whenComplete((data, error) -> runSync(() -> {
            if (error != null || data == null) {
                plugin.getMessageManager().send(sender, "gradient-operation-failed");
                return;
            }
            sendList(sender, args[2], data);
        }));
    }

    private void sendList(CommandSender sender, String playerName, PlayerTagData data) {
        List<String> owned = data.getGradientData().validEffects(System.currentTimeMillis());
        plugin.getMessageManager().send(sender, "gradient-admin-list-header", "player", playerName,
                "count", String.valueOf(owned.size()));
        if (owned.isEmpty()) {
            plugin.getMessageManager().send(sender, "gradient-admin-list-empty");
            return;
        }
        String selected = data.getGradientData().selectedEffect(System.currentTimeMillis());
        for (String id : owned) {
            GradientEffect effect = plugin.getGradientManager().getEffect(id);
            plugin.getMessageManager().send(sender, "gradient-admin-list-entry", MessageManager.of(
                    "effect", effect != null ? effect.displayName() : id,
                    "id", id,
                    "expire", plugin.getMessageManager().formatExpireTime(data.getGradientData().expireTime(id)),
                    "selected", id.equals(selected) ? "*" : ""
            ));
        }
    }

    private OfflinePlayer findPlayer(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getMessageManager().send(sender, "player-not-found", "player", name);
            return null;
        }
        return target;
    }

    private void sendNotFound(CommandSender sender, String effectId) {
        plugin.getMessageManager().send(sender, "gradient-not-found", "effect", effectId);
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private void runSync(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
