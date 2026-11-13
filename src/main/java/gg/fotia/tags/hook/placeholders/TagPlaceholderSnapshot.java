package gg.fotia.tags.hook.placeholders;

import gg.fotia.tags.gradient.GradientEffect;
import gg.fotia.tags.gradient.GradientFrameRenderer;
import gg.fotia.tags.gradient.GradientTarget;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.WrappedTagText;

import java.util.List;
import java.util.UUID;

/** 不可变称号快照，可供异步 PAPI 请求读取；过期判断不依赖缓存刷新。 */
record TagPlaceholderSnapshot(UUID uuid, boolean online, boolean hidden, Values selected, Values fallback,
                              long selectedExpires, List<Long> ownedExpiries, GradientEffect effect,
                              long effectExpires, boolean wrapped, String left, String right) {
    static final TagPlaceholderSnapshot MISSING = new TagPlaceholderSnapshot(null, false, false,
            Values.EMPTY, Values.EMPTY, -1, List.of(), null, 0, false, "", "");

    TagPlaceholderSnapshot {
        ownedExpiries = List.copyOf(ownedExpiries);
    }

    String value(String field, long now) {
        if (field.equals("count")) {
            return Long.toString(ownedExpiries.stream().filter(expiry -> expiry == -1 || expiry > now).count());
        }
        if (hidden) return "";
        Values display = selectedExpires != -1 && now >= selectedExpires ? fallback : selected;
        String text = switch (field) {
            case "prefix" -> render(display, display.prefix(), true, now);
            case "suffix" -> render(display, display.suffix(), false, now);
            case "prefix2" -> render(display, display.prefix2(), true, now);
            case "suffix2" -> render(display, display.suffix2(), false, now);
            case "tag_name" -> display.name();
            case "tag" -> display.id();
            default -> "";
        };
        return LegacyColorConverter.convertToLegacy(text);
    }

    private String render(Values display, String text, boolean prefix, long now) {
        GradientTarget target = prefix ? GradientTarget.PREFIX : GradientTarget.SUFFIX;
        if (effect == null || !effect.enabled() || !effect.appliesTo(target)
                || effectExpires != -1 && now > effectExpires) return text;
        String body = display.custom() ? (prefix ? display.rawPrefix() : display.rawSuffix()) : text;
        String rendered = GradientFrameRenderer.render(body, effect, now / 50L);
        return display.custom() && wrapped ? WrappedTagText.format(rendered, left, right) : rendered;
    }

    record Values(String id, String name, String prefix, String suffix, String prefix2, String suffix2,
                  boolean custom, String rawPrefix, String rawSuffix) {
        static final Values EMPTY = new Values("", "", "", "", "", "", false, "", "");
    }
}
