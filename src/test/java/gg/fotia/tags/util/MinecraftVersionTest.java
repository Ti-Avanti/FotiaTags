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
}
