package gg.fotia.tags.hook.placeholders;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderQueryTest {
    @Test
    void preservesExistingFieldsAndPlayerNamesContainingUnderscores() {
        assertEquals(new PlaceholderQuery("prefix", null), PlaceholderQuery.parse("PREFIX"));
        assertEquals(new PlaceholderQuery("tag_name", "Steve_One"), PlaceholderQuery.parse("tag_name_Steve_One"));
        assertEquals(new PlaceholderQuery("prefix2", "Steve"), PlaceholderQuery.parse("prefix2_Steve"));
        assertNull(PlaceholderQuery.parse("unknown_Steve"));
    }

    @Test
    void acceptsAnotherExpansionsIdentityWithoutDependingOnItsName() {
        assertEquals(new PlaceholderQuery("prefix", "{example_leaderboard_0_uuid}"),
                PlaceholderQuery.parse("prefix_{example_leaderboard_0_uuid}"));
        assertNull(PlaceholderQuery.identity("{example_leaderboard_0_uuid}"));
        assertNull(PlaceholderQuery.identity("%example_player%"));
        assertNull(PlaceholderQuery.identity("<red>Steve"));
    }

    @Test
    void normalizesNamesAndBothUuidFormatsAndRejectsInvalidIdentities() {
        assertEquals("steve_one", PlaceholderQuery.identity(" Steve_One "));
        assertEquals(".bedrock", PlaceholderQuery.identity(".Bedrock"));
        String uuid = "34ada67e-9127-338c-9b2c-bb170122957e";
        assertEquals(uuid, PlaceholderQuery.identity(uuid.toUpperCase()));
        assertEquals(uuid, PlaceholderQuery.identity(uuid.replace("-", "")));
        assertNull(PlaceholderQuery.identity("1-1-1-1-1"));
        assertNull(PlaceholderQuery.identity("Steve' OR 1=1"));
        assertNull(PlaceholderQuery.identity(""));
        assertNull(PlaceholderQuery.parse("prefix_" + "x".repeat(300)));
    }
}
