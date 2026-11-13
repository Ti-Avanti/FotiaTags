package gg.fotia.tags.hook.placeholders;

import gg.fotia.tags.util.TextComponentParser;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

/** 重载时一次性读取配置，异步查询只读取不可变设置。 */
record TargetPlaceholderSettings(boolean enabled, long onlineTtl, long offlineTtl, int capacity,
                                 int maxPending, String loading, String unavailable) {
    static TargetPlaceholderSettings read(ConfigurationSection config) {
        String path = "placeholders.target-player.";
        return new TargetPlaceholderSettings(config.getBoolean(path + "enabled", true),
                clamp(config.getLong(path + "online-cache-ticks", 1), 1, 200) * 50L,
                clamp(config.getLong(path + "offline-cache-seconds", 30), 1, 3600) * 1000L,
                (int) clamp(config.getInt(path + "max-cache-size", 1000), 1, 100000),
                (int) clamp(config.getInt(path + "max-pending-requests", 64), 1, 1000),
                text(config.getString(path + "loading-text", "")),
                text(config.getString(path + "unavailable-text", "")));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String text(String value) {
        return LegacyComponentSerializer.builder().character('\u00a7').hexColors()
                .useUnusualXRepeatedCharacterHexFormat().build().serialize(TextComponentParser.parse(value));
    }
}
