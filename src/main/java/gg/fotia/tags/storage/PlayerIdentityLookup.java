package gg.fotia.tags.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** 从已有玩家档案查找身份，避免通过 Bukkit 离线玩家查询触发网络请求。 */
final class PlayerIdentityLookup {
    private PlayerIdentityLookup() {}

    static Optional<UUID> find(Connection connection, String identity) throws SQLException {
        boolean uuid = identity.length() == 36;
        String condition = uuid ? "uuid = ?" : "LOWER(player_name) = LOWER(?)";
        try (var statement = connection.prepareStatement(
                "SELECT uuid FROM fotiatags_players WHERE " + condition + " LIMIT 2")) {
            statement.setString(1, identity);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                UUID result = UUID.fromString(rows.getString("uuid"));
                // 名称重用导致多个档案同名时不猜测，调用方可改用 UUID。
                return rows.next() ? Optional.empty() : Optional.of(result);
            }
        }
    }
}
