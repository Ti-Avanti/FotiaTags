package gg.fotia.tags.tag;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTagDataTest {

    @Test
    void storesMultipleCustomTagsAndClearsSelectedTagWhenOneIsDeleted() {
        PlayerTagData data = new PlayerTagData(UUID.randomUUID());
        CustomTag first = new CustomTag("custom:first", "皇帝", "", "nametag", "", "vault", 1000.0);
        CustomTag second = new CustomTag("custom:second", "剑圣", "", "nametag", "", "playerpoints", 50.0);

        data.addCustomTag(first);
        data.addCustomTag(second);
        data.setCurrentTag("custom:first");

        assertEquals(2, data.getCustomTags().size());
        assertTrue(data.hasCustomTag("custom:first"));
        assertTrue(data.hasCustomTag("custom:second"));

        CustomTag removed = data.removeCustomTag("custom:first");

        assertSame(first, removed);
        assertNull(data.getCurrentTag());
        assertFalse(data.hasCustomTag("custom:first"));
        assertTrue(data.hasCustomTag("custom:second"));
    }
}
