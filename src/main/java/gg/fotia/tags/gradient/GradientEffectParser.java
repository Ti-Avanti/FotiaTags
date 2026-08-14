package gg.fotia.tags.gradient;

import gg.fotia.tags.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class GradientEffectParser {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> NAMED_COLORS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
    );

    private GradientEffectParser() {
    }

    public static GradientEffect parse(String id, ConfigurationSection config) {
        List<String> colors = config.getStringList("colors").stream()
                .map(GradientEffectParser::normalizeColor)
                .filter(color -> !color.isEmpty())
                .toList();
        if (colors.size() < 2) {
            throw new IllegalArgumentException("Gradient effect " + id + " requires at least two valid colors");
        }

        return new GradientEffect(
                id,
                config.getString("display-name", id),
                config.getBoolean("enabled", true),
                colors,
                Math.max(4, config.getInt("duration-ticks", 80)),
                config.getBoolean("reverse", false),
                parseTargets(config.getStringList("apply-to")),
                parsePurchase(id, config.getConfigurationSection("purchase")),
                parseIcon(config.getConfigurationSection("gui"))
        );
    }

    private static Set<GradientTarget> parseTargets(List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return EnumSet.allOf(GradientTarget.class);
        }

        EnumSet<GradientTarget> targets = EnumSet.noneOf(GradientTarget.class);
        for (String value : configured) {
            try {
                targets.add(GradientTarget.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return targets.isEmpty() ? EnumSet.allOf(GradientTarget.class) : targets;
    }

    private static GradientPurchase parsePurchase(String id, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false)) {
            return GradientPurchase.disabled();
        }

        String provider = section.getString("provider", "vault").toLowerCase(Locale.ROOT);
        if (!provider.equals("vault") && !provider.equals("playerpoints")) {
            throw new IllegalArgumentException("Gradient effect " + id + " has an invalid payment provider");
        }

        String durationText = section.getString("duration", "permanent");
        long duration = TimeUtil.parseDuration(durationText);
        if (duration == Long.MIN_VALUE || duration != -1L && duration > Long.MAX_VALUE - System.currentTimeMillis()) {
            throw new IllegalArgumentException("Gradient effect " + id + " has an invalid purchase duration");
        }

        double price = section.getDouble("price", 0.0);
        if (!Double.isFinite(price) || price < 0.0) {
            throw new IllegalArgumentException("Gradient effect " + id + " has an invalid purchase price");
        }
        if (provider.equals("playerpoints") && price != Math.rint(price)) {
            throw new IllegalArgumentException("Gradient effect " + id + " requires an integer PlayerPoints price");
        }

        return new GradientPurchase(true, provider, price, duration);
    }

    private static GradientIcon parseIcon(ConfigurationSection section) {
        if (section == null) {
            return GradientIcon.defaults();
        }
        return new GradientIcon(
                section.getString("material", "NAME_TAG"),
                section.getString("item-model", ""),
                section.getString("tooltip-style", ""),
                section.getInt("custom-model-data", 0),
                section.getBoolean("glow", false)
        );
    }

    private static String normalizeColor(String raw) {
        if (raw == null) {
            return "";
        }
        String color = raw.trim();
        if (color.startsWith("&#")) {
            color = color.substring(1);
        }
        color = color.toLowerCase(Locale.ROOT);
        return HEX_COLOR.matcher(color).matches() || NAMED_COLORS.contains(color) ? color : "";
    }
}
