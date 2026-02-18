package gg.fotia.tags.storage;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.PlayerTagData;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLiteManager implements DatabaseManager {

    private final FotiaTags plugin;
    private Connection connection;

    public SQLiteManager(FotiaTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "data.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            createTables();
            plugin.getLogger().info("SQLite database connected!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    current_tag VARCHAR(64),
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_owned (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid VARCHAR(36) NOT NULL,
                    tag_id VARCHAR(64) NOT NULL,
                    expire_time BIGINT DEFAULT -1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(uuid, tag_id)
                )
            """);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<PlayerTagData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerTagData data = new PlayerTagData(uuid);

            try {
                // 加载当前称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT current_tag FROM fotiatags_players WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        data.setCurrentTag(rs.getString("current_tag"));
                    }
                }

                // 加载拥有的称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT tag_id, expire_time FROM fotiatags_owned WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        data.addTag(rs.getString("tag_id"), rs.getLong("expire_time"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player data: " + e.getMessage());
            }

            return data;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerTagData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 保存当前称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR REPLACE INTO fotiatags_players (uuid, current_tag, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                    stmt.setString(1, data.getUuid().toString());
                    stmt.setString(2, data.getCurrentTag());
                    stmt.executeUpdate();
                }

                // 删除旧的称号数据
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_owned WHERE uuid = ?")) {
                    stmt.setString(1, data.getUuid().toString());
                    stmt.executeUpdate();
                }

                // 保存新的称号数据
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_owned (uuid, tag_id, expire_time) VALUES (?, ?, ?)")) {
                    for (var entry : data.getOwnedTags().entrySet()) {
                        stmt.setString(1, data.getUuid().toString());
                        stmt.setString(2, entry.getKey());
                        stmt.setLong(3, entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save player data: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> addPlayerTag(UUID uuid, String tagId, long expireTime) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 确保玩家记录存在
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR IGNORE INTO fotiatags_players (uuid) VALUES (?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                // 添加称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR REPLACE INTO fotiatags_owned (uuid, tag_id, expire_time) VALUES (?, ?, ?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId);
                    stmt.setLong(3, expireTime);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to add player tag: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> removePlayerTag(UUID uuid, String tagId) {
        return CompletableFuture.runAsync(() -> {
            try {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_owned WHERE uuid = ? AND tag_id = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId);
                    stmt.executeUpdate();
                }

                // 如果删除的是当前称号，清除当前称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET current_tag = NULL WHERE uuid = ? AND current_tag = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove player tag: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Void> setSelectedTag(UUID uuid, String tagId) {
        return CompletableFuture.runAsync(() -> {
            try {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR REPLACE INTO fotiatags_players (uuid, current_tag, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set selected tag: " + e.getMessage());
            }
        });
    }
}
