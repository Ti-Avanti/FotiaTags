package gg.fotia.tags.storage;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.TagManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                    player_name VARCHAR(16),
                    current_tag VARCHAR(64),
                    current_gradient VARCHAR(64),
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

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fotiatags_gradient_owned (
                    uuid VARCHAR(36) NOT NULL,
                    effect_id VARCHAR(64) NOT NULL,
                    expire_time BIGINT DEFAULT -1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, effect_id)
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
            createCustomTagsTable("fotiatags_custom_tags_new");
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    INSERT INTO fotiatags_custom_tags_new
                        (uuid, custom_tag_id, prefix, suffix, icon_id, particle_effect, payment_provider, purchase_price, created_at, updated_at)
                    SELECT uuid,
                           CONCAT('custom:', LOWER(SUBSTRING(REPLACE(uuid, '-', ''), 1, 12))),
                           prefix,
                           suffix,
                           icon_id,
                           '',
                           '',
                           0,
                           created_at,
                           updated_at
                    FROM fotiatags_custom_tags
                """);
                stmt.execute("""
                    UPDATE fotiatags_players p
                    JOIN fotiatags_custom_tags_new c ON c.uuid = p.uuid
                    SET p.current_tag = c.custom_tag_id
                    WHERE p.current_tag = '__custom__'
                """);
                stmt.execute("DROP TABLE fotiatags_custom_tags");
                stmt.execute("RENAME TABLE fotiatags_custom_tags_new TO fotiatags_custom_tags");
            }
            return;
        }

        addMissingCustomTagColumns();
    }

    private void createCustomTagsTable() throws SQLException {
        createCustomTagsTable("fotiatags_custom_tags");
    }

    private void createCustomTagsTable(String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    uuid VARCHAR(36) NOT NULL,
                    custom_tag_id VARCHAR(80) NOT NULL,
                    prefix TEXT NOT NULL,
                    suffix TEXT NOT NULL,
                    icon_id VARCHAR(64) NOT NULL,
                    particle_effect VARCHAR(64) DEFAULT '',
                    payment_provider VARCHAR(32) DEFAULT '',
                    purchase_price DOUBLE DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, custom_tag_id)
                )
            """.formatted(tableName));
        }
    }

    private void addMissingCustomTagColumns() throws SQLException {
        addColumnIfMissing("fotiatags_custom_tags", "particle_effect", "VARCHAR(64) DEFAULT ''");
        addColumnIfMissing("fotiatags_custom_tags", "payment_provider", "VARCHAR(32) DEFAULT ''");
        addColumnIfMissing("fotiatags_custom_tags", "purchase_price", "DOUBLE DEFAULT 0");
    }

    private void addMissingPlayerColumns() throws SQLException {
        addColumnIfMissing("fotiatags_players", "player_name", "VARCHAR(16)");
        addColumnIfMissing("fotiatags_players", "current_gradient", "VARCHAR(64)");
    }

    private boolean tableExists(String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, table, null)) {
            return tables.next();
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!hasColumn(table, column)) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
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
                        "SELECT current_tag, current_gradient FROM fotiatags_players WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        data.setCurrentTag(rs.getString("current_tag"));
                        data.getGradientData().setSelectedEffectUnchecked(rs.getString("current_gradient"));
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

                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT effect_id, expire_time FROM fotiatags_gradient_owned WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        data.getGradientData().grant(rs.getString("effect_id"), rs.getLong("expire_time"));
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
        String currentGradient = data.getGradientData().selectedEffect(System.currentTimeMillis());
        Map<String, Long> ownedGradients = new HashMap<>(data.getGradientData().ownedEffects());

        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                // 保存当前称号
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_players (uuid, current_tag, current_gradient) VALUES (?, ?, ?) " +
                                "ON DUPLICATE KEY UPDATE current_tag = VALUES(current_tag), " +
                                "current_gradient = VALUES(current_gradient)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, currentTag);
                    stmt.setString(3, currentGradient);
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

                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_gradient_owned WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_gradient_owned (uuid, effect_id, expire_time) VALUES (?, ?, ?)")) {
                    for (var entry : ownedGradients.entrySet()) {
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

    @Override
    public CompletableFuture<Void> grantGradientEffect(UUID uuid, String effectId, long expireTime) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();
                try (PreparedStatement player = connection.prepareStatement(
                        "INSERT IGNORE INTO fotiatags_players (uuid) VALUES (?)");
                     PreparedStatement effect = connection.prepareStatement(
                             "INSERT INTO fotiatags_gradient_owned (uuid, effect_id, expire_time) VALUES (?, ?, ?) " +
                                     "ON DUPLICATE KEY UPDATE expire_time = VALUES(expire_time)")) {
                    player.setString(1, uuid.toString());
                    player.executeUpdate();
                    effect.setString(1, uuid.toString());
                    effect.setString(2, effectId);
                    effect.setLong(3, expireTime);
                    effect.executeUpdate();
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> removeGradientEffect(UUID uuid, String effectId) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            boolean autoCommit = true;
            try {
                ensureConnection();
                autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement effect = connection.prepareStatement(
                        "DELETE FROM fotiatags_gradient_owned WHERE uuid = ? AND effect_id = ?");
                     PreparedStatement selected = connection.prepareStatement(
                             "UPDATE fotiatags_players SET current_gradient = NULL WHERE uuid = ? AND current_gradient = ?")) {
                    effect.setString(1, uuid.toString());
                    effect.setString(2, effectId);
                    effect.executeUpdate();
                    selected.setString(1, uuid.toString());
                    selected.setString(2, effectId);
                    selected.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackException) {
                        e.addSuppressed(rollbackException);
                    }
                }
                throw new CompletionException(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.setAutoCommit(autoCommit);
                    } catch (SQLException restoreException) {
                        plugin.getLogger().severe("Failed to restore MySQL auto-commit: " + restoreException.getMessage());
                    }
                }
            }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> setSelectedGradientEffect(UUID uuid, String effectId) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            try {
                ensureConnection();
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_players (uuid, current_gradient) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE current_gradient = VALUES(current_gradient)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, effectId);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
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
                ensureConnection();

                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT IGNORE INTO fotiatags_players (uuid) VALUES (?)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        """
                        INSERT INTO fotiatags_custom_tags
                            (uuid, custom_tag_id, prefix, suffix, icon_id, particle_effect, payment_provider, purchase_price)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            prefix = VALUES(prefix),
                            suffix = VALUES(suffix),
                            icon_id = VALUES(icon_id),
                            particle_effect = VALUES(particle_effect),
                            payment_provider = VALUES(payment_provider),
                            purchase_price = VALUES(purchase_price)
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
    public CompletableFuture<Void> deleteCustomTag(UUID uuid, String customTagId) {
        return CompletableFuture.runAsync(() -> {
            synchronized (lock) {
            boolean autoCommit = true;
            try {
                ensureConnection();
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
                        "UPDATE fotiatags_players SET current_tag = NULL WHERE uuid = ? AND current_tag = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, customTagId);
                    stmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackException) {
                        e.addSuppressed(rollbackException);
                    }
                }
                plugin.getLogger().severe("Failed to delete custom tag: " + e.getMessage());
                throw new CompletionException(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.setAutoCommit(autoCommit);
                    } catch (SQLException restoreException) {
                        plugin.getLogger().severe("Failed to restore MySQL auto-commit: " + restoreException.getMessage());
                    }
                }
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
                ensureConnection();
                autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);

                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM fotiatags_custom_tags WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE fotiatags_players SET current_tag = NULL WHERE uuid = ? AND (current_tag = ? OR current_tag LIKE 'custom:%')")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, TagManager.CUSTOM_TAG_ID);
                    stmt.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                if (connection != null) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackException) {
                        e.addSuppressed(rollbackException);
                    }
                }
                plugin.getLogger().severe("Failed to delete custom tag: " + e.getMessage());
                throw new CompletionException(e);
            } finally {
                if (connection != null) {
                    try {
                        connection.setAutoCommit(autoCommit);
                    } catch (SQLException restoreException) {
                        plugin.getLogger().severe("Failed to restore MySQL auto-commit: " + restoreException.getMessage());
                    }
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
                ensureConnection();

                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO fotiatags_players (uuid, player_name) VALUES (?, ?) " +
                                "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name)")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, playerName);
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
                ensureConnection();

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
