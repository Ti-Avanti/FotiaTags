package gg.fotia.tags.gui.futureui;

import gg.fotia.tags.util.TextComponentParser;
import gg.fotia.futureui.render.PixelCanvas;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** 保留称号颜色，并按文字宽度裁切；不让长前后缀挤出卡片。 */
final class TagMenuText {
    private TagMenuText() {}
    static Component preview(String value, String fallback, int width) {
        String plain = PlainTextComponentSerializer.plainText().serialize(TextComponentParser.parse(value));
        // 原解析器不认识的扩展标签不展示给玩家；图片可由管理员单独映射。
        return text(plain.contains("<image:") || plain.contains("%image_") ? fallback : value, width);
    }
    static Component text(String value, int width) {
        Component parsed = TextComponentParser.parse(value == null ? "" : value);
        if (PixelCanvas.measure(parsed) <= width) return parsed;
        int[] remaining = { Math.max(0, width - 9) };
        return crop(parsed, remaining, false).append(Component.text("…"));
    }
    private static Component crop(Component source, int[] remaining, boolean inheritedBold) {
        boolean bold = source.decoration(TextDecoration.BOLD) == TextDecoration.State.NOT_SET
                ? inheritedBold : source.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        String content = source instanceof TextComponent text ? text.content()
                : PlainTextComponentSerializer.plainText().serialize(source.children(java.util.List.of()));
        Component result = Component.empty().style(source.style());
        for (int point : content.codePoints().toArray()) {
            Component letter = Component.text(new String(Character.toChars(point))).style(source.style());
            int width = PixelCanvas.measure(letter.decoration(TextDecoration.BOLD, bold));
            if (width > remaining[0]) { remaining[0] = 0; return result; }
            remaining[0] -= width; result = result.append(letter);
        }
        for (Component child : source.children()) {
            if (remaining[0] <= 0) break;
            result = result.append(crop(child, remaining, bold));
        }
        return result;
    }
    static String amount(double value) {
        return Math.rint(value) == value ? String.valueOf((long)value) : String.format(Locale.ROOT, "%.2f", value);
    }
}
