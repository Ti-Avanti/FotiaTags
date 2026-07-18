package gg.fotia.tags.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TextComponentParserTest {

    @Test
    void parsesLegacyCodesAndFallsBackForMalformedMiniMessage() {
        Component parsed = TextComponentParser.parse("&a\u79f0\u53f7");
        assertEquals("\u00a7a\u79f0\u53f7", LegacyComponentSerializer.legacySection().serialize(parsed));

        assertDoesNotThrow(() -> TextComponentParser.parse("<click:run_command:'broken'"));
    }
}
