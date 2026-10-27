package gg.fotia.tags.tag;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelRewardOwnershipTest {
    @Test void losingRankingKeepsPurchasedTitle() {
        var data = new PlayerTagData(UUID.randomUUID()); data.addTag("champion", -1); data.setCurrentTag("champion");
        data.externalTags(Map.of("champion", System.currentTimeMillis() + 60000)); data.externalTags(Map.of());
        assertTrue(data.hasTag("champion")); assertEquals("champion", data.getCurrentTag()); assertEquals(-1, data.getTagExpireTime("champion"));
    }
    @Test void externalOwnershipIsNeverSavedAsPurchasedAndExpires() {
        var data = new PlayerTagData(UUID.randomUUID()); data.externalTags(Map.of("champion", System.currentTimeMillis() + 60000)); data.setCurrentTag("champion");
        assertTrue(data.hasTag("champion")); assertTrue(data.getOwnedTags().isEmpty()); assertTrue(data.getValidTags().contains("champion"));
        data.externalTags(Map.of("champion", 1L)); assertFalse(data.hasTag("champion")); assertNull(data.getCurrentTag());
    }
}
