package gg.fotia.tags.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeMiniMessageValidatorTest {

    @Test
    void permitsOnlyColorsAndDecorations() {
        assertTrue(SafeMiniMessageValidator.isSafe("<red><bold>\u7687\u5e1d</bold></red>"));
        assertTrue(SafeMiniMessageValidator.isSafe("<#12abEF>\u5251\u5723</#12abEF>"));
        assertFalse(SafeMiniMessageValidator.isSafe("<click:run_command:'/op me'>\u70b9\u51fb</click>"));
        assertFalse(SafeMiniMessageValidator.isSafe("<hover:show_text:'secret'>\u60ac\u6d6e</hover>"));
        assertFalse(SafeMiniMessageValidator.isSafe("<insertion:'/kill'>\u63d2\u5165</insertion>"));
    }
}
