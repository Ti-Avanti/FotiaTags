package gg.fotia.tags.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {

    @Test
    void comparesReleaseAndQualifiedBukkitVersions() {
        assertTrue(MinecraftVersion.isAtLeast("1.21.4-R0.1-SNAPSHOT", 1, 21, 4));
        assertTrue(MinecraftVersion.isAtLeast("1.21.11", 1, 21, 4));
        assertFalse(MinecraftVersion.isAtLeast("1.20.6-R0.1-SNAPSHOT", 1, 21, 4));
    }

    @Test
    void comparesCalendarVersionsWithoutDroppingTraditionalReleases() {
        assertTrue(MinecraftVersion.isAtLeast("26.2", 1, 21, 4));
        assertTrue(MinecraftVersion.isAtLeast("26.3", 26, 2, 0));
        assertTrue(MinecraftVersion.isAtLeast("26.2-R0.1-SNAPSHOT", 26, 2, 0));
        assertTrue(MinecraftVersion.isAtLeast("26.3.1", 26, 3, 1));
        assertFalse(MinecraftVersion.isAtLeast("26.2", 26, 3, 0));
        assertFalse(MinecraftVersion.isAtLeast("26.3", 26, 3, 1));
        assertFalse(MinecraftVersion.isAtLeast("1.21.11", 26, 2, 0));
        assertTrue(MinecraftVersion.isAtLeast("1.20", 1, 20, 0));
        assertFalse(MinecraftVersion.isAtLeast("1.20.4", 1, 21, 4));
    }

    @Test
    void doesNotEnableModernFeaturesForUnrecognizedVersions() {
        assertFalse(MinecraftVersion.isAtLeast(null, 1, 21, 4));
        assertFalse(MinecraftVersion.isAtLeast("", 1, 21, 4));
        assertFalse(MinecraftVersion.isAtLeast("26.invalid", 1, 21, 4));
        assertFalse(MinecraftVersion.isAtLeast("999999999999.2", 1, 21, 4));
    }
}
