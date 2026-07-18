package gg.fotia.tags.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SafeMiniMessageValidator {

    private static final Pattern TAG_PATTERN = Pattern.compile("<([^<>]+)>");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white",
            "bold", "b", "italic", "i", "underlined", "u",
            "strikethrough", "st", "obfuscated", "obf", "reset"
    );

    private SafeMiniMessageValidator() {
    }

    public static boolean isSafe(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }

        Matcher matcher = TAG_PATTERN.matcher(input);
        StringBuilder plainText = new StringBuilder();
        int start = 0;
        while (matcher.find()) {
            plainText.append(input, start, matcher.start());
            if (!isAllowedTag(matcher.group(1))) {
                return false;
            }
            start = matcher.end();
        }
        plainText.append(input, start, input.length());
        return plainText.indexOf("<") < 0 && plainText.indexOf(">") < 0;
    }

    private static boolean isAllowedTag(String rawTag) {
        String tag = rawTag.trim();
        if (tag.startsWith("/")) {
            tag = tag.substring(1);
        }
        if (tag.startsWith("!")) {
            tag = tag.substring(1);
        }

        int argumentIndex = tag.indexOf(':');
        String name = (argumentIndex >= 0 ? tag.substring(0, argumentIndex) : tag)
                .toLowerCase(Locale.ROOT);
        return HEX_COLOR.matcher(name).matches() || ALLOWED_TAGS.contains(name);
    }
}
