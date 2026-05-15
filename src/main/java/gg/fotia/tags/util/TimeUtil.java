package gg.fotia.tags.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhdwMy])");
    private static final long SECOND_MILLIS = 1000L;
    private static final long MINUTE_MILLIS = 60L * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    /**
     * 解析时长字符串
     * @param input 时长字符串，如 "1d", "7d", "30m", "permanent"
     * @return 毫秒数，-1表示永久，Long.MIN_VALUE表示无效格式
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return Long.MIN_VALUE;
        }

        input = input.trim();
        String lowerInput = input.toLowerCase();

        // 永久
        if (lowerInput.equals("permanent") || lowerInput.equals("perm") || lowerInput.equals("-1") || lowerInput.equals("forever")) {
            return -1;
        }

        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return Long.MIN_VALUE;
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }

        String unit = matcher.group(2);

        long multiplier = switch (unit) {
            case "s" -> SECOND_MILLIS;
            case "m" -> MINUTE_MILLIS;
            case "h" -> HOUR_MILLIS;
            case "d" -> DAY_MILLIS;
            case "w" -> 7L * DAY_MILLIS;
            case "M" -> 30L * DAY_MILLIS;
            case "y" -> 365L * DAY_MILLIS;
            default -> Long.MIN_VALUE;
        };
        if (multiplier == Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }

        try {
            return Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException e) {
            return Long.MIN_VALUE;
        }
    }

}
