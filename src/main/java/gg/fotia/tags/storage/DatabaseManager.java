package gg.fotia.tags.storage;

import gg.fotia.tags.tag.PlayerTagData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseManager {

    void initialize();

    void close();

    CompletableFuture<PlayerTagData> loadPlayerData(UUID uuid);

    CompletableFuture<Void> savePlayerData(PlayerTagData data);

    CompletableFuture<Void> addPlayerTag(UUID uuid, String tagId, long expireTime);

    CompletableFuture<Void> removePlayerTag(UUID uuid, String tagId);

    CompletableFuture<Void> setSelectedTag(UUID uuid, String tagId);
}
