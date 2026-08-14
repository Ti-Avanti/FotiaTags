package gg.fotia.tags.hook;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.gradient.GradientTarget;
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
            case "prefix" -> plugin.getGradientManager().render(player.getUniqueId(),
                    plugin.getTagManager().getCurrentPrefix(player.getUniqueId()), GradientTarget.PREFIX);
            case "suffix" -> plugin.getGradientManager().render(player.getUniqueId(),
                    plugin.getTagManager().getCurrentSuffix(player.getUniqueId()), GradientTarget.SUFFIX);
            case "prefix2" -> plugin.getGradientManager().render(player.getUniqueId(),
                    plugin.getTagManager().getCurrentPrefix2(player.getUniqueId()), GradientTarget.PREFIX);
            case "suffix2" -> plugin.getGradientManager().render(player.getUniqueId(),
                    plugin.getTagManager().getCurrentSuffix2(player.getUniqueId()), GradientTarget.SUFFIX);
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
