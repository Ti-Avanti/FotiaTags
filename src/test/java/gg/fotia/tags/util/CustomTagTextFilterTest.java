package gg.fotia.tags.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomTagTextFilterTest {

    @Test
    void blocksWordsSplitByLegacyHexColors() {
        CustomTagTextFilter filter = new CustomTagTextFilter(
                true,
                true,
                true,
                true,
                List.of("违禁词"),
                List.of()
        );

        assertTrue(filter.findViolation("&c违&#ffaa00禁§e词").isPresent());
    }

    @Test
    void supportsCaseInsensitiveRegexRulesAfterMiniMessageIsStripped() {
        CustomTagTextFilter filter = new CustomTagTextFilter(
                true,
                true,
                true,
                true,
                List.of(),
                List.of("bad\\s*word")
        );

        assertTrue(filter.findViolation("<red>BAD</red> word").isPresent());
    }
}
