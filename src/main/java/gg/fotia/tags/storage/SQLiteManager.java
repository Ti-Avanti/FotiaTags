package gg.fotia.tags.storage;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.TagManager;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class SQLiteManager implements DatabaseManager {

    private final FotiaTags plugin;
    private Connection connection;
    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "FotiaTags-SQLite");
        thread.setDaemon(true);
        return thread;
    });

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
            throw new IllegalStateException("SQLite database initialization failed", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16),
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
        migrateOrCreateCustomTagsTable();
        addMissingPlayerColumns();
    }

    private void migrateOrCreateCustomTagsTable() throws SQLException {
        if (!tableExists("fotiatags_custom_tags")) {
            createCustomTagsTable();
            return;
        }

        if (!hasColumn("fotiatags_custom_tags", "custom_tag_id")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE fotiatags_custom_tags RENAME TO fotiatags_custom_tags_legacy");
            }
            createCustomTagsTable();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    INSERT INTO fotiatags_custom_tags
                        (uuid, custom_tag_id, prefix, suffix, icon_id, particle_effect, payment_provider, purchase_price, created_at, updated_at)
                    SELECT uuid,
                           'custom:' || lower(substr(replace(uuid, '-', ''), 1, 12)),
                           prefix,
                           suffix,
                           icon_id,
                           '',
                           '',
                           0,
                           created_at,
                           updated_at
                    FROM fotiatags_custom_tags_legacy
                """);
                stmt.execute("""
                    UPDATE fotiatags_players
                    SET current_tag = (
                        SELECT custom_tag_id
                        FROM fotiatags_custom_tags
                        WHERE fotiatags_custom_tags.uuid = fotiatags_players.uuid
                        LIMIT 1
                    )
                    WHERE current_tag = '__custom__'
                      AND EXISTS (
                          SELECT 1
                          FROM fotiatags_custom_tags
                          WHERE fotiatags_custom_tags.uuid = fotiatags_players.uuid
                      )
                """);
                stmt.execute("DROP TABLE fotiatags_custom_tags_legacy");
            }
            return;
        }

        addMissingCustomTagColumns();
    }

    private void createCustomTagsTable() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_custom_tags (
                    uuid VARCHAR(36) NOT NULL,
                    custom_tag_id VARCHAR(80) NOT NULL,
                    prefix TEXT NOT NULL,
                    suffix TEXT NOT NULL,
                    icon_id VARCHAR(64) NOT NULL,
                    particle_effect VARCHAR(64) DEFAULT '',
                    payment_provider VARCHAR(32) DEFAULT '',
                    purchase_price DOUBLE DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, custom_tag_id)
                )
            """);
        }
    }

    private void addMissingCustomTagColumns() throws SQLException {
        addColumnIfMissing("fotiatags_custom_tags", "particle_effect", "VARCHAR(64) DEFAULT ''");
        addColumnIfMissing("fotiatags_custom_tags", "payment_provider", "VARCHAR(32) DEFAULT ''");
        addColumnIfMissing("fotiatags_custom_tags", "purchase_price", "DOUBLE DEFAULT 0");
    }

    private void addMissingPlayerColumns() throws SQLException {
        if (!hasColumn("fotiatags_players", "player_name")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE fotiatags_players ADD COLUMN player_name VARCHAR(16)");
            }
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("PRAGMA table_info(" + table + ")")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tableExists(String table) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!hasColumn(table, column)) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
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
            plugin.getLogger().severe("Interrupted while closing SQLite executor: " + e.getMessage());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close SQLite connection: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<PlayerTagData> loadPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerTagData data = new PlayerTagData(uuid);

            synchronized (lock) {
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

                try (PreparedStatement stmt = connection.prepareStatement("""
                        SELECT custom_tag_id, prefix, suffix, icon_id, particle_effect, payment_provider, purchase_price
                        FROM fotiatags_custom_tags
                        WHERE uuid = ?
                        ORDER BY created_at, custom_tag_id
                        """)) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        data.addCustomTag(new CustomTag(
                                rs.getString("custom_tag_id"),
                                rs.getString("prefix"),
                                rs.getString("suffix"),
                                rs.getString("icon_id"),
                                rs.getString("particle_effect"),
                                rs.getString("payment_provider"),
                                rs.getDouble("purchase_price")
                        ));
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
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                // 保存当前称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR IGNORE INTO fotiatags_players (uuid) VALUES (?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET current_tag = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?")) {
                    stmt.setString(1, currentTag);
                    stmt.setString(2, uuid.toString());
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
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR IGNORE INTO fotiatags_players (uuid) VALUES (?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET current_tag = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?")) {
                    stmt.setString(1, tagId);
                    stmt.setString(2, uuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set selected tag: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveCustomTag(UUID uuid, CustomTag customTag) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR IGNORE INTO fotiatags_players (uuid) VALUES (?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        """
                        INSERT INTO fotiatags_custom_tags
                            (uuid, custom_tag_id, prefix, suffix, icon_id, particle_effect, payment_provider, purchase_price, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT(uuid, custom_tag_id) DO UPDATE SET
                            prefix = excluded.prefix,
                            suffix = excluded.suffix,
                            icon_id = excluded.icon_id,
                            particle_effect = excluded.particle_effect,
                            payment_provider = excluded.payment_provider,
                            purchase_price = excluded.purchase_price,
                            updated_at = CURRENT_TIMESTAMP
                        """)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, customTag.getId());
                    stmt.setString(3, customTag.getPrefix());
                    stmt.setString(4, customTag.getSuffix());
                    stmt.setString(5, customTag.getIconId());
                    stmt.setString(6, customTag.getParticleEffect());
                    stmt.setString(7, customTag.getPaymentProvider());
                    stmt.setDouble(8, customTag.getPurchasePrice());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save custom tag: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteCustomTag(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            boolean autoCommit = true;
            try {
                autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_custom_tags WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET current_tag = NULL, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND (current_tag = ? OR current_tag LIKE 'custom:%')")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, TagManager.CUSTOM_TAG_ID);
                    stmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                plugin.getLogger().severe("Failed to delete custom tag: " + e.getMessage());
                throw new CompletionException(e);
            } finally {
                try {
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException restoreException) {
                    plugin.getLogger().severe("Failed to restore SQLite auto-commit: " + restoreException.getMessage());
                }
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteCustomTag(UUID uuid, String customTagId) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            boolean autoCommit = true;
            try {
                autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_custom_tags WHERE uuid = ? AND custom_tag_id = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, customTagId);
                    if (stmt.executeUpdate() == 0) {
                        throw new SQLException("Custom tag does not exist: " + customTagId);
                    }
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET current_tag = NULL, updated_at = CURRENT_TIMESTAMP WHERE uuid = ? AND current_tag = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, customTagId);
                    stmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                plugin.getLogger().severe("Failed to delete custom tag: " + e.getMessage());
                throw new CompletionException(e);
            } finally {
                try {
                    connection.setAutoCommit(autoCommit);
                } catch (SQLException restoreException) {
                    plugin.getLogger().severe("Failed to restore SQLite auto-commit: " + restoreException.getMessage());
                }
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> savePlayerProfile(UUID uuid, String playerName) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT OR IGNORE INTO fotiatags_players (uuid, player_name, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET player_name = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?")) {
                    stmt.setString(1, playerName);
                    stmt.setString(2, uuid.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save player profile: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlayerProfile>> loadPlayerProfiles() {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock) {
            try {
                List<PlayerProfile> profiles = new ArrayList<>();
                try (PreparedStatement stmt = connection.prepareStatement("""
                        SELECT p.uuid,
                               p.player_name,
                               p.current_tag,
                                COUNT(DISTINCT o.tag_id) AS owned_count,
                                CASE WHEN COUNT(c.custom_tag_id) = 0 THEN 0 ELSE 1 END AS has_custom
                        FROM fotiatags_players p
                        LEFT JOIN fotiatags_owned o
                               ON o.uuid = p.uuid AND (o.expire_time = -1 OR o.expire_time > ?)
                        LEFT JOIN fotiatags_custom_tags c ON c.uuid = p.uuid
                        GROUP BY p.uuid, p.player_name, p.current_tag
                        ORDER BY LOWER(COALESCE(p.player_name, p.uuid))
                        """)) {
                    stmt.setLong(1, System.currentTimeMillis());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        profiles.add(new PlayerProfile(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("player_name"),
                                rs.getString("current_tag"),
                                rs.getInt("owned_count"),
                                rs.getInt("has_custom") == 1
                        ));
                    }
                }
                return profiles;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load player profiles: " + e.getMessage());
                throw new CompletionException(e);
            }
            }
        }, executor);
    }
}
