package gg.fotia.tags.gradient;

import java.util.Locale;
import java.util.regex.Pattern;

public final class GradientFrameRenderer {

    private static final Pattern LEGACY_HEX_SEQUENCE = Pattern.compile("(?i)[&§]x(?:[&§][0-9a-f]){6}");
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)[&§]#[0-9a-f]{6}");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)[&§][0-9a-fr]");
    private static final Pattern MINI_MESSAGE_COLOR = Pattern.compile(
            "(?i)</?(?:gradient|rainbow|color|colour)(?::[^>]*)?>|" +
                    "</?(?:#[0-9a-f]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|" +
                    "gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>"
    );

    private GradientFrameRenderer() {
    }

    public static String render(String text, GradientEffect effect, long tick) {
        String safeText = text == null ? "" : text;
        if (safeText.isEmpty() || effect == null || !effect.enabled() || effect.colors().size() < 2) {
            return safeText;
        }

        safeText = removeStaticColors(safeText);
        String colors = String.join(":", effect.colors());
        double phase = GradientAnimation.phaseAt(tick, effect.durationTicks(), effect.reversed());
        return "<gradient:" + colors + ":" + String.format(Locale.ROOT, "%.4f", phase) + ">"
                + safeText
                + "</gradient>";
    }

    private static String removeStaticColors(String text) {
        String result = LEGACY_HEX_SEQUENCE.matcher(text).replaceAll("");
        result = LEGACY_HEX.matcher(result).replaceAll("");
        result = LEGACY_COLOR.matcher(result).replaceAll("");
        return MINI_MESSAGE_COLOR.matcher(result).replaceAll("");
    }
}
