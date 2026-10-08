package gg.fotia.tags.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** 分别解析装饰和正文，避免正文的颜色、渐变或格式延续到包裹符号。 */
public final class WrappedTagText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private WrappedTagText() {}

    public static String format(String text, String left, String right) {
        if (text == null || text.isEmpty()) return "";
        Component result = Component.empty()
                .append(TextComponentParser.parse(left))
                .append(TextComponentParser.parse(text))
                .append(TextComponentParser.parse(right));
        return MINI_MESSAGE.serialize(result);
    }
}
