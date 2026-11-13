package gg.fotia.tags.hook.placeholders;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** 解析称号字段和可选的目标玩家表达式，不依赖任何其他插件的变量名称。 */
public record PlaceholderQuery(String field, String target) {
    private static final String[] FIELDS = {"tag_name", "prefix2", "suffix2", "prefix", "suffix", "count", "tag"};
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.]{1,16}");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** 识别已有字段或“字段_目标”，未知字段返回 null。 */
    public static PlaceholderQuery parse(String params) {
        if (params == null || params.length() > 280) return null;
        String lower = params.toLowerCase(Locale.ROOT);
        for (String field : FIELDS) {
            if (lower.equals(field)) return new PlaceholderQuery(field, null);
            if (lower.startsWith(field + "_")) {
                return new PlaceholderQuery(field, params.substring(field.length() + 1));
            }
        }
        return null;
    }

    /** 规范化玩家名及带连字符或不带连字符的 UUID；未解析的变量和无效输入返回 null。 */
    public static String identity(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                    + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        if (UUID_TEXT.matcher(value).matches()) return UUID.fromString(value).toString();
        return NAME.matcher(value).matches() ? value.toLowerCase(Locale.ROOT) : null;
    }
}
