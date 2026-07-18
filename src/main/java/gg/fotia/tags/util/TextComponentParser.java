package gg.fotia.tags.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class TextComponentParser {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextComponentParser() {
    }

    public static Component parse(String text) {
        String value = text == null ? "" : text;
        try {
            return MINI_MESSAGE.deserialize("<!i>" + LegacyColorConverter.convertToMiniMessage(value));
        } catch (RuntimeException exception) {
            return Component.text(value);
        }
    }
}
