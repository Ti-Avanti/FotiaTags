package gg.fotia.tags.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyColorConverterTest {

    @Test
    void serializesLegacyAndMiniMessageColorsForPlaceholderConsumers() {
        assertEquals("\u00a7a\u7687\u5e1d", LegacyColorConverter.convertToLegacy("&a\u7687\u5e1d"));

        String hex = LegacyColorConverter.convertToLegacy("&#123456\u5251\u5723");
        assertEquals("\u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76\u5251\u5723", hex);
        assertFalse(LegacyColorConverter.convertToLegacy("<red>\u79f0\u53f7</red>").contains("<red>"));
    }

    @Test
    void preservesUnknownDownstreamTagsWhileConvertingColors() {
        assertEquals("\u00a7a<image:rank:admin_prefix>Admin",
                LegacyColorConverter.convertToLegacy("&a<image:rank:admin_prefix>Admin"));
    }
}
