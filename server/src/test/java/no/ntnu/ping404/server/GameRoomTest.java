package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameRoomTest {

    // ------------------------------------------------------------------
    // Test Constants
    // ------------------------------------------------------------------

    private static final String ROOM_ID = "room-1";
    private static final int HOST_ID = 1;

    // Player names
    private static final String NAME_ALICE = "Alice";
    private static final String NAME_BOB = "Bob";
    private static final String NAME_CHARLIE = "Charlie";
    private static final String NAME_NEO = "Neo";
    private static final String NAME_TRINITY = "Trinity";

    // Connection IDs for primary tests
    private static final int CONN_PLAYER_1 = 1;
    private static final int CONN_PLAYER_2 = 2;
    private static final int CONN_PLAYER_3 = 3;

    // Connection IDs for slot/host tests
    private static final int CONN_HOST_42 = 42;
    private static final int CONN_GUEST_99 = 99;
    private static final int CONN_NEO = 100;
    private static final int CONN_TRINITY = 200;
    private static final int CONN_ALT_P1 = 10;
    private static final int CONN_ALT_P2 = 20;

    // Win score thresholds
    private static final int DEFAULT_WIN_SCORE = 5;
    private static final int CUSTOM_WIN_SCORE_7 = 7;
    private static final int CUSTOM_WIN_SCORE_10 = 10;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static GameRoom newRoom() {
        return new GameRoom(ROOM_ID, HOST_ID);
    }

    private static PlayerConnection conn(int id) {
        return new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
    }

    private static Player player(int id, String name) {
        return new Player(id, name);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.2")
    void roomIsCreatedWithWaitingStatus() {
        // FR1.2: A freshly constructed GameRoom must have Phase.WAITING.
        assertEquals(GameState.Phase.WAITING, newRoom().getPhase());
    }

    @Test
    @Tag("FR1.2")
    void roomIsEmptyBeforeAnyPlayerJoins() {
        // FR1.2: Newly created room has no connections â€” isEmpty() must return true.
        assertTrue(newRoom().isEmpty());
    }

    @Test
    @Tag("FR1.3")
    void playerIsAddedToRoomOnJoin() {
        // FR1.3: addPlayer() must return true and the room's player count must increase to 1.
        GameRoom room = newRoom();
        boolean added = room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        assertTrue(added);
        assertEquals(1, room.getPlayerCount());
    }

    @Test
    @Tag("FR4.5")
    void playerDisplayNameIsStoredInPlayerObject() {
        // FR4.5: Player.getName() must equal the value supplied at construction.
        Player p = player(CONN_PLAYER_1, NAME_ALICE);
        assertEquals(NAME_ALICE, p.getName());
    }

    @Test
    @Tag("FR1.4")
    void roomIsNotFullWithOnlyOnePlayer() {
        // FR1.4: With one player, isFull() must return false.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        assertFalse(room.isFull());
    }

    @Test
    @Tag("FR1.4")
    void roomIsFullAfterTwoPlayersJoin() {
        // FR1.4: After two players join, isFull() must return true.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));
        assertTrue(room.isFull());
    }

    @Test
    @Tag("FR1.4")
    void matchCannotStartWithFewerThanTwoPlayers() {
        // FR1.4: canStart() must return false if only one player is present.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        assertFalse(room.canStart());
    }

    @Test
    @Tag("FR1.4")
    void matchCanStartWhenTwoPlayersConnected() {
        // FR1.4: canStart() must return true once exactly two players are connected.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));
        assertTrue(room.canStart());
    }

    @Test
    @Tag("FR1.3")
    void addPlayerReturnsFalseWhenRoomIsAlreadyFull() {
        // FR1.3: A third player must not be added to a full room.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));
        boolean added = room.addPlayer(conn(CONN_PLAYER_3), player(CONN_PLAYER_3, NAME_CHARLIE));
        assertFalse(added);
        assertEquals(2, room.getPlayerCount());
    }

    @Test
    @Tag("FR4.3")
    void roomBecomesEmptyAfterAllPlayersRemoved() {
        // FR4.3: After all players leave, isEmpty() must return true.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));
        room.removePlayer(CONN_PLAYER_1);
        room.removePlayer(CONN_PLAYER_2);
        assertTrue(room.isEmpty());
    }

    @Test
    @Tag("FR4.3")
    void opponentIsStillPresentAfterOnePlayerDisconnects() {
        // FR4.3: When one player disconnects, the opponent's connection must still be in the room.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));
        room.removePlayer(CONN_PLAYER_1);
        assertFalse(room.isEmpty());
        assertTrue(room.getConnections().containsKey(CONN_PLAYER_2));
    }

    @Test
    @Tag("FR1.4")
    void matchCannotStartAfterRoomStatusChangedToPlaying() {
        // FR1.4: Once the room's status is PLAYING, canStart() must return false.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        assertFalse(room.canStart());
    }

    @Test
    @Tag("FR1.2")
    void hostConnectionIdIsPreservedAfterRoomCreation() {
        // FR1.2: The host's connection ID must be stored and retrievable.
        GameRoom room = new GameRoom(ROOM_ID, CONN_HOST_42);
        assertEquals(CONN_HOST_42, room.getHostConnectionId());
    }

    @Test
    @Tag("A1")
    @Tag("FR4.1")
    void remainingPlayerBecomesHostWhenOriginalHostDisconnects() {
        // A1 / FR4.1: If the host disconnects, the remaining player must become host.
        GameRoom room = new GameRoom(ROOM_ID, CONN_PLAYER_1);
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));

        room.removePlayer(CONN_PLAYER_1);

        assertEquals(CONN_PLAYER_2, room.getHostConnectionId(),
                "Remaining player should be promoted to host when original host disconnects");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.2")
    void hostRemainsUnchangedWhenNonHostDisconnects() {
        // A1 / FR4.2: If non-host disconnects, the current host must remain host.
        GameRoom room = new GameRoom(ROOM_ID, CONN_PLAYER_1);
        room.addPlayer(conn(CONN_PLAYER_1), player(CONN_PLAYER_1, NAME_ALICE));
        room.addPlayer(conn(CONN_PLAYER_2), player(CONN_PLAYER_2, NAME_BOB));

        room.removePlayer(CONN_PLAYER_2);

        assertEquals(CONN_PLAYER_1, room.getHostConnectionId(),
                "Host should remain unchanged when non-host disconnects");
    }

    // 
    // Slot-to-Player Mapping Tests (FR3.2)
    // 
    // Score.getWinner() returns 1 or 2 (slot numbers), but GameOver needs
    // actual player connection IDs and names. These tests document that
    // GameRoom must provide a mapping from slot â†’ actual player.
    // This is critical for correctly identifying the winner in GameOver packets.

    @Test
    @Tag("FR3.2")
    void firstPlayerAddedIsAssignedSlot1() {
        // FR3.2: GameOver must identify the winner by their actual ID.
        // The first player added to the room should be "slot 1" for scoring purposes.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_HOST_42), player(CONN_HOST_42, NAME_ALICE));
        room.addPlayer(conn(CONN_GUEST_99), player(CONN_GUEST_99, NAME_BOB));

        int slot1ConnectionId = room.getConnectionIdForSlot(1);
        assertEquals(CONN_HOST_42, slot1ConnectionId, 
                "First player added (connection 42) should be in slot 1");
    }

    @Test
    @Tag("FR3.2")
    void secondPlayerAddedIsAssignedSlot2() {
        // FR3.2: GameOver must identify the winner by their actual ID.
        // The second player added to the room should be "slot 2" for scoring purposes.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_HOST_42), player(CONN_HOST_42, NAME_ALICE));
        room.addPlayer(conn(CONN_GUEST_99), player(CONN_GUEST_99, NAME_BOB));

        int slot2ConnectionId = room.getConnectionIdForSlot(2);
        assertEquals(CONN_GUEST_99, slot2ConnectionId, 
            "Second player added (connection 99) should be in slot 2");
    }

    @Test
    @Tag("FR3.2")
    void winnerSlotMapsToActualPlayerConnectionId() {
        // FR3.2: When Score.getWinner() returns a slot number,
        // we must be able to map it to the actual player's connection ID.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_NEO), player(CONN_NEO, NAME_NEO));
        room.addPlayer(conn(CONN_TRINITY), player(CONN_TRINITY, NAME_TRINITY));
        
        // Simulate player in slot 1 winning
        room.getGameState().getScore().setPlayer1Score(
                room.getGameState().getScore().getWinningScore());
        
        int winnerSlot = room.getGameState().getScore().getWinner();
        assertEquals(1, winnerSlot, "Slot 1 should be the winner");
        
        int winnerConnectionId = room.getConnectionIdForSlot(winnerSlot);
        assertEquals(CONN_NEO, winnerConnectionId,
                "Winner slot 1 should map to connection ID 100");
    }

    @Test
    @Tag("FR3.2")
    void slotAssignmentIsStableAfterPlayerDisconnect() {
        // FR3.2: Slot assignments must remain stable even if a player disconnects.
        // If player in slot 1 disconnects, slot 1 should still refer to them (or be empty),
        // not reassign slot 2's player to slot 1.
        GameRoom room = newRoom();
        room.addPlayer(conn(CONN_ALT_P1), player(CONN_ALT_P1, NAME_ALICE));
        room.addPlayer(conn(CONN_ALT_P2), player(CONN_ALT_P2, NAME_BOB));
        
        // Alice (slot 1) disconnects
        room.removePlayer(CONN_ALT_P1);
        
        int slot2ConnectionId = room.getConnectionIdForSlot(2);
        assertEquals(CONN_ALT_P2, slot2ConnectionId,
                "Bob should remain in slot 2 after Alice disconnects");
    }

    // ------------------------------------------------------------------
    // Win Score Configuration Tests (FR1.6 / M3)
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    void roomUsesDefaultWinScoreWhenNotSpecified() {
        // FR1.6: When no winScore is provided, default of 5 is used.
        GameRoom room = new GameRoom(ROOM_ID, HOST_ID);
        assertEquals(DEFAULT_WIN_SCORE, room.getWinScore(),
                "Default win score should be 5");
        assertEquals(DEFAULT_WIN_SCORE, room.getGameState().getScore().getWinningScore(),
                "GameState Score should also have win score of 5");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    void roomUsesCustomWinScoreWhenSpecified() {
        // FR1.6: Host can configure custom win score at room creation.
        GameRoom room = new GameRoom(ROOM_ID, HOST_ID, CUSTOM_WIN_SCORE_10);
        assertEquals(CUSTOM_WIN_SCORE_10, room.getWinScore(),
                "Custom win score should be stored");
        assertEquals(CUSTOM_WIN_SCORE_10, room.getGameState().getScore().getWinningScore(),
                "GameState Score should use custom win score");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    void roomWinScoreIsPassedToGameState() {
        // M3: Win score flows from room config to GameState's Score.
        GameRoom room = new GameRoom(ROOM_ID, HOST_ID, CUSTOM_WIN_SCORE_7);
        
        // Match should end when player reaches the configured threshold
        room.getGameState().getScore().setPlayer1Score(CUSTOM_WIN_SCORE_7);
        assertTrue(room.getGameState().getScore().hasWinner(),
                "Match should end at configured win score");
    }
}

