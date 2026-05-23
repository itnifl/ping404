package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration tests for GameRoom functionality.
 * These tests verify basic room creation and state without network I/O.
 */
class GameRoomSimpleIntegrationTests {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final int CONN_ID_1 = 1;
    private static final int CONN_ID_2 = 2;
    private static final int CONN_ID_3 = 3;
    private static final int HOST_CONN_ID = 42;
    private static final int ALT_CONN_ID = 100;

    private static final String NAME_ALICE = "Alice";
    private static final String NAME_BOB = "Bob";
    private static final String NAME_HOST = "Host";
    private static final String NAME_GUEST = "Guest";
    private static final String NAME_ALICE_AGAIN = "AliceAgain";
    private static final String NAME_CHARLIE = "Charlie";

    private static final String ROOM_ID = "room-1";
    private static final String ROOM_ID_42 = "room-42";
    private static final String ROOM_PREFIX = "room-";

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PlayerConnection conn(int id) {
        return new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
    }

    private static Player player(int id, String name) {
        return new Player(id, name);
    }

    // ------------------------------------------------------------------
    // FR1.2 â€” Room creation and initial state
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.2")
    @DisplayName("Room ID follows expected naming pattern")
    void roomIdFollowsNamingPattern() {
        // FR1.2: Room IDs should follow a consistent naming pattern for debugging.
        GameRoom room = new GameRoom(ROOM_ID_42, CONN_ID_1);
        
        assertEquals(ROOM_ID_42, room.getRoomId());
        assertTrue(room.getRoomId().startsWith(ROOM_PREFIX));
    }

    @Test
    @Tag("FR1.2")
    @DisplayName("Newly created room has correct initial state")
    void newlyCreatedRoomHasCorrectInitialState() {
        // FR1.2: Verify all initial state properties of a freshly created room.
        GameRoom room = new GameRoom(ROOM_ID, ALT_CONN_ID);

        assertAll("Initial room state",
            () -> assertEquals(GameState.Phase.WAITING, room.getPhase(), "Status should be WAITING"),
            () -> assertTrue(room.isEmpty(), "Room should be empty"),
            () -> assertFalse(room.isFull(), "Room should not be full"),
            () -> assertFalse(room.canStart(), "Room should not be startable"),
            () -> assertEquals(0, room.getPlayerCount(), "Player count should be 0"),
            () -> assertNotNull(room.getGameState(), "GameState should exist"),
            () -> assertNotNull(room.getConnections(), "Connections map should exist")
        );
    }

    @Test
    @Tag("FR1.2")
    @DisplayName("Room GameState is initialized and accessible")
    void roomGameStateIsInitializedAndAccessible() {
        // FR1.2: Each room must have its own GameState instance.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        
        assertNotNull(room.getGameState());
        assertEquals(0, room.getGameState().getPlayerCount());
    }

    // ------------------------------------------------------------------
    // FR4.1 / FR4.2 â€” Host tracking
    // ------------------------------------------------------------------

    @Test
    @Tag("FR4.1")
    @DisplayName("First player is correctly marked as host")
    void firstPlayerIsCorrectlyMarkedAsHost() {
        // FR4.1: The connection ID used at room creation is the host.
        int hostConnectionId = HOST_CONN_ID;
        GameRoom room = new GameRoom(ROOM_ID, hostConnectionId);
        
        assertEquals(hostConnectionId, room.getHostConnectionId());
    }

    @Test
    @Tag("FR4.2")
    @DisplayName("Host ID remains unchanged when second player joins")
    void hostIdRemainsUnchangedWhenSecondPlayerJoins() {
        // FR4.2: When the second player joins, the original host ID must not change.
        int hostConnectionId = CONN_ID_1;
        GameRoom room = new GameRoom(ROOM_ID, hostConnectionId);
        
        room.addPlayer(conn(hostConnectionId), player(hostConnectionId, NAME_HOST));
        room.addPlayer(conn(CONN_ID_2), player(CONN_ID_2, NAME_GUEST));
        
        assertEquals(hostConnectionId, room.getHostConnectionId(), "Host should remain player 1");
    }

    // ------------------------------------------------------------------
    // FR1.3 â€” Player joining
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.3")
    @DisplayName("First player join returns success")
    void firstPlayerJoinReturnsSuccess() {
        // FR1.3: The first addPlayer() call on an empty room must succeed.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        
        boolean added = room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        
        assertTrue(added, "First player should be added successfully");
    }

    @Test
    @Tag("FR1.3")
    @DisplayName("Player is added to both connections and GameState")
    void playerIsAddedToBothConnectionsAndGameState() {
        // FR1.3: addPlayer() must update both the connections map and the GameState.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        
        room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        
        assertEquals(1, room.getPlayerCount(), "Room player count");
        assertEquals(1, room.getGameState().getPlayerCount(), "GameState player count");
        assertTrue(room.getConnections().containsKey(CONN_ID_1), "Connections should contain player 1");
    }

    @Test
    @Tag("FR1.3")
    @DisplayName("Room is no longer empty after player joins")
    void roomIsNoLongerEmptyAfterPlayerJoins() {
        // FR1.3: isEmpty() must return false after a player joins.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        assertTrue(room.isEmpty());
        
        room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        
        assertFalse(room.isEmpty());
    }

    // ------------------------------------------------------------------
    // FR1.4 â€” canStart() trigger
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.4")
    @DisplayName("canStart returns true when exactly 2 players connected and status is WAITING")
    void canStartReturnsTrueWhenTwoPlayersConnectedAndWaiting() {
        // FR1.4: canStart() must return true only when 2 players are present and status is WAITING.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        
        room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        assertFalse(room.canStart(), "Should not start with 1 player");
        
        room.addPlayer(conn(CONN_ID_2), player(CONN_ID_2, NAME_BOB));
        assertTrue(room.canStart(), "Should be able to start with 2 players");
    }

    @Test
    @Tag("FR1.4")
    @DisplayName("canStart returns false when status is not WAITING")
    void canStartReturnsFalseWhenStatusIsNotWaiting() {
        // FR1.4: canStart() must be false if the room is no longer WAITING.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        room.addPlayer(conn(CONN_ID_2), player(CONN_ID_2, NAME_BOB));
        
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        assertFalse(room.canStart(), "Should not start when already PLAYING");
        
        room.setPhase(GameState.Phase.PAUSED);
        assertFalse(room.canStart(), "Should not start when PAUSED");
        
        room.setPhase(GameState.Phase.PLAYING); // resume from PAUSED
        room.setPhase(GameState.Phase.FINISHED);
        assertFalse(room.canStart(), "Should not start when FINISHED");
    }

    @Test
    @Tag("FR1.4")
    @DisplayName("isFull returns true immediately after second player joins")
    void isFullReturnsTrueImmediatelyAfterSecondPlayerJoins() {
        // FR1.4: isFull() must return true as soon as the second player is added.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        assertFalse(room.isFull());
        
        room.addPlayer(conn(CONN_ID_2), player(CONN_ID_2, NAME_BOB));
        assertTrue(room.isFull());
    }

    // ------------------------------------------------------------------
    // FR4.3 â€” Room status transitions
    // ------------------------------------------------------------------

    @Test
    @Tag("FR4.3")
    @DisplayName("Room status can be changed via setStatus")
    void roomStatusCanBeChangedViaSetStatus() {
        // FR4.3: Room status should be mutable for game flow control.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE));
        room.addPlayer(conn(CONN_ID_2), player(CONN_ID_2, NAME_BOB));
        
        assertEquals(GameState.Phase.WAITING, room.getPhase());
        
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        assertEquals(GameState.Phase.PLAYING, room.getPhase());
        
        room.setPhase(GameState.Phase.PAUSED);
        assertEquals(GameState.Phase.PAUSED, room.getPhase());
        
        room.setPhase(GameState.Phase.PLAYING); // resume from PAUSED
        room.setPhase(GameState.Phase.FINISHED);
        assertEquals(GameState.Phase.FINISHED, room.getPhase());
    }

    // ------------------------------------------------------------------
    // FR1.3 â€” Duplicate player prevention
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.3")
    @DisplayName("Same connection ID cannot be added twice")
    void sameConnectionIdCannotBeAddedTwice() {
        // FR1.3: Adding a player with a duplicate connection ID must fail.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        
        assertTrue(room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE)));
        assertFalse(room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE_AGAIN)));
        
        assertEquals(1, room.getPlayerCount(), "Player count should remain 1");
    }

    // ------------------------------------------------------------------
    // FR1.4 â€” Room capacity enforcement
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.4")
    @DisplayName("Third player cannot join a full room")
    void thirdPlayerCannotJoinFullRoom() {
        // FR1.4: A room with MAX_PLAYERS (2) must reject additional players.
        GameRoom room = new GameRoom(ROOM_ID, CONN_ID_1);
        
        assertTrue(room.addPlayer(conn(CONN_ID_1), player(CONN_ID_1, NAME_ALICE)));
        assertTrue(room.addPlayer(conn(CONN_ID_2), player(CONN_ID_2, NAME_BOB)));
        assertFalse(room.addPlayer(conn(CONN_ID_3), player(CONN_ID_3, NAME_CHARLIE)));
        
        assertEquals(2, room.getPlayerCount());
        assertFalse(room.getConnections().containsKey(CONN_ID_3));
    }
}

