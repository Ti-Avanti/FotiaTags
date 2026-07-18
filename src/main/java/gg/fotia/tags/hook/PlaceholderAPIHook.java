package gg.fotia.tags.hook;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.util.LegacyColorConverter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final FotiaTags plugin;

    public PlaceholderAPIHook(FotiaTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fotiatags";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Fotia";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String result = switch (params.toLowerCase()) {
            case "prefix" -> plugin.getTagManager().getCurrentPrefix(player.getUniqueId());
            case "suffix" -> plugin.getTagManager().getCurrentSuffix(player.getUniqueId());
            case "prefix2" -> plugin.getTagManager().getCurrentPrefix2(player.getUniqueId());
            case "suffix2" -> plugin.getTagManager().getCurrentSuffix2(player.getUniqueId());
            case "tag" -> plugin.getTagManager().getCurrentTagId(player.getUniqueId());
            case "tag_name" -> plugin.getTagManager().getCurrentTagName(player.getUniqueId());
            case "count" -> String.valueOf(plugin.getTagManager().getOwnedTagCount(player.getUniqueId()));
            default -> null;
        };

        // PlaceholderAPI消费者通常按旧版颜色码解析，避免向CMI等插件返回原始MiniMessage标签。
        if (result != null) {
            result = LegacyColorConverter.convertToLegacy(result);
        }

        return result;
    }
}
