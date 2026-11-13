package gg.fotia.tags.storage;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerIdentityLookupTest {
    @Test
    void findsStoredPlayersWithoutInventingUuidsAndRejectsAmbiguousNames() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            connection.createStatement().execute("CREATE TABLE fotiatags_players (uuid TEXT PRIMARY KEY, player_name TEXT)");
            UUID id = UUID.randomUUID();
            try (var insert = connection.prepareStatement("INSERT INTO fotiatags_players VALUES (?, ?)")) {
                insert.setString(1, id.toString()); insert.setString(2, "Steve_One"); insert.executeUpdate();
                assertEquals(id, PlayerIdentityLookup.find(connection, "steve_one").orElseThrow());
                assertEquals(id, PlayerIdentityLookup.find(connection, id.toString()).orElseThrow());
                assertTrue(PlayerIdentityLookup.find(connection, "Missing").isEmpty());
                assertTrue(PlayerIdentityLookup.find(connection, "' OR 1=1 --").isEmpty());
                insert.setString(1, UUID.randomUUID().toString()); insert.executeUpdate();
                assertTrue(PlayerIdentityLookup.find(connection, "Steve_One").isEmpty());
                assertEquals(id, PlayerIdentityLookup.find(connection, id.toString()).orElseThrow());
            }
        }
    }
}
