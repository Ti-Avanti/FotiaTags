package gg.fotia.tags.gradient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerGradientDataTest {

    @Test
    void selectingRequiresANonExpiredOwnedEffect() {
        PlayerGradientData data = new PlayerGradientData();
        data.grant("royal", 2_000L);

        assertTrue(data.select("royal", 1_000L));
        assertEquals("royal", data.selectedEffect(1_000L));
        assertNull(data.selectedEffect(2_001L));
        assertFalse(data.select("missing", 1_000L));
    }

    @Test
    void removingTheSelectedEffectUnequipsIt() {
        PlayerGradientData data = new PlayerGradientData();
        data.grant("royal", -1L);
        data.select("royal", 1_000L);

        data.remove("royal");

        assertNull(data.selectedEffect(1_000L));
        assertTrue(data.validEffects(1_000L).isEmpty());
    }

    @Test
    void clearingSelectionKeepsOwnership() {
        PlayerGradientData data = new PlayerGradientData();
        data.grant("royal", -1L);
        data.select("royal", 1_000L);

        data.clearSelection();

        assertNull(data.selectedEffect(1_000L));
        assertTrue(data.owns("royal", 1_000L));
    }
}
