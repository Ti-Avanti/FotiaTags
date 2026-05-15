package gg.fotia.tags.storage;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.PlayerTagData;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class MySQLManager implements DatabaseManager {

    private final FotiaTags plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private Connection connection;
    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FotiaTags-MySQL");
        thread.setDaemon(true);
        return thread;
    });

    public MySQLManager(FotiaTags plugin, String host, int port, String database, String username, String password) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    @Override
    public void initialize() {
        try {
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8";
            connection = DriverManager.getConnection(url, username, password);

            createTables();
            plugin.getLogger().info("MySQL database connected!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to MySQL database: " + e.getMessage());
            throw new IllegalStateException("MySQL database initialization failed", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    current_tag VARCHAR(64),
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_owned (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    uuid VARCHAR(36) NOT NULL,
                    tag_id VARCHAR(64) NOT NULL,
                    expire_time BIGINT DEFAULT -1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY unique_player_tag (uuid, tag_id)
                )
            """);
        }
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            initialize();
        }
    }

    @Override
    public void close() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            synchronized (lock) {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted while closing MySQL executor: " + e.getMessage());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close MySQL connection: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<PlayerTagData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerTagData data = new PlayerTagData(uuid);

            synchronized (lock) {
            try {
                ensureConnection();

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
                throw new CompletionException(e);
            }
            }

            return data;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerTagData data) {
        UUID uuid = data.getUuid();
        String currentTag = data.getCurrentTag();
        Map<String, Long> ownedTags = new HashMap<>(data.getOwnedTags());

        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                // 保存当前称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_players (uuid, current_tag) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE current_tag = VALUES(current_tag)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, currentTag);
                    stmt.executeUpdate();
                }

                // 删除旧的称号数据
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_owned WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                // 保存新的称号数据
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_owned (uuid, tag_id, expire_time) VALUES (?, ?, ?)")) {
                    for (var entry : ownedTags.entrySet()) {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, entry.getKey());
                        stmt.setLong(3, entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                connection.commit();
                connection.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException rollbackException) {
                    plugin.getLogger().severe("Failed to rollback player data save: " + rollbackException.getMessage());
                }
                plugin.getLogger().severe("Failed to save player data: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> addPlayerTag(UUID uuid, String tagId, long expireTime) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();

                // 确保玩家记录存在
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT IGNORE INTO fotiatags_players (uuid) VALUES (?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                // 添加称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_owned (uuid, tag_id, expire_time) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE expire_time = VALUES(expire_time)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId);
                    stmt.setLong(3, expireTime);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to add player tag: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removePlayerTag(UUID uuid, String tagId) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();

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
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> setSelectedTag(UUID uuid, String tagId) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();

                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_players (uuid, current_tag) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE current_tag = VALUES(current_tag)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, tagId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set selected tag: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }
}
