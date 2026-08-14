package gg.fotia.tags.storage;

import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.CustomTag;

import java.util.List;
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

    CompletableFuture<Void> grantGradientEffect(UUID uuid, String effectId, long expireTime);

    CompletableFuture<Void> removeGradientEffect(UUID uuid, String effectId);

    CompletableFuture<Void> setSelectedGradientEffect(UUID uuid, String effectId);

    CompletableFuture<Void> saveCustomTag(UUID uuid, CustomTag customTag);

    CompletableFuture<Void> deleteCustomTag(UUID uuid);

    CompletableFuture<Void> deleteCustomTag(UUID uuid, String customTagId);

    CompletableFuture<Void> savePlayerProfile(UUID uuid, String playerName);

    CompletableFuture<List<PlayerProfile>> loadPlayerProfiles();
}
