package gg.fotia.tags.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOperationLockTest {

    @Test
    void preventsDuplicateOperationsUntilReleased() {
        PlayerOperationLock lock = new PlayerOperationLock();
        UUID uuid = UUID.randomUUID();

        assertTrue(lock.tryAcquire(uuid));
        assertFalse(lock.tryAcquire(uuid));

        lock.release(uuid);

        assertTrue(lock.tryAcquire(uuid));
    }
}
