package gg.fotia.tags.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhdwMy])");

    /**
     * 解析时长字符串
     * @param input 时长字符串，如 "1d", "7d", "30m", "permanent"
     * @return 毫秒数，-1表示永久，Long.MIN_VALUE表示无效格式
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return Long.MIN_VALUE;
        }

        input = input.toLowerCase().trim();

        // 永久
        if (input.equals("permanent") || input.equals("perm") || input.equals("-1") || input.equals("forever")) {
            return -1;
        }

        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return Long.MIN_VALUE;
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "s" -> amount * 1000L;
            case "m" -> amount * 60 * 1000L;
            case "h" -> amount * 60 * 60 * 1000L;
            case "d" -> amount * 24 * 60 * 60 * 1000L;
            case "w" -> amount * 7 * 24 * 60 * 60 * 1000L;
            case "M" -> amount * 30 * 24 * 60 * 60 * 1000L;
            case "y" -> amount * 365 * 24 * 60 * 60 * 1000L;
            default -> Long.MIN_VALUE;
        };
    }

    /**
     * 格式化时长
     * @param millis 毫秒数
     * @return 格式化的时长字符串
     */
    public static String formatDuration(long millis) {
        if (millis == -1) {
            return "永久";
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            hours %= 24;
            if (hours > 0) {
                return days + "天" + hours + "小时";
            }
            return days + "天";
        }

        if (hours > 0) {
            minutes %= 60;
            if (minutes > 0) {
                return hours + "小时" + minutes + "分钟";
            }
            return hours + "小时";
        }

        if (minutes > 0) {
            seconds %= 60;
            if (seconds > 0) {
                return minutes + "分钟" + seconds + "秒";
            }
            return minutes + "分钟";
        }

        return seconds + "秒";
    }

    /**
     * 格式化过期时间
     * @param expireTime 过期时间戳，-1表示永久
     * @return 格式化的过期时间字符串
     */
    public static String formatExpireTime(long expireTime) {
        if (expireTime == -1) {
            return "永久";
        }

        long remaining = expireTime - System.currentTimeMillis();
        if (remaining <= 0) {
            return "已过期";
        }

        return formatDuration(remaining);
    }
}
