package gg.fotia.tags.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerOperationLock {

    private final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(UUID uuid) {
        return uuid != null && lockedPlayers.add(uuid);
    }

    public boolean isLocked(UUID uuid) { return uuid != null && lockedPlayers.contains(uuid); }

    public void release(UUID uuid) {
        if (uuid != null) {
            lockedPlayers.remove(uuid);
        }
    }
}
