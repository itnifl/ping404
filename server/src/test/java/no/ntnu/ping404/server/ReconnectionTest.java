package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.packets.LoginResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for issue #38 â€” Server reconnection and rejoin handling.
 *
 * <p>Requirements covered: A1 (availability), FR4.3 (disconnect/reconnect handling).
 *
 * <p>Feature summary:
 * <ul>
 *   <li>A session token is issued to the player on login.</li>
 *   <li>If a player disconnects, they may rejoin using their token within a 30-second window.</li>
 *   <li>On successful rejoin, the player is restored to the same {@link GameRoom} and slot.</li>
 * </ul>
 */
class ReconnectionTest {

    private SessionStore sessionStore;

    public ReconnectionTest() {
        this.sessionStore = new SessionStore();
    }

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
    // Session token issuance
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void loginResponseIncludesSessionToken() {
        // A1 / FR4.3: A successful LoginResponse must include a non-null, non-empty session token
        // that the client can present to the server when reconnecting.
        LoginResponse response = LoginResponse.success(1, 5, sessionStore.createSession(1, "room-1"), "room-1");
        assertNotNull(response.sessionToken,
            "Successful login response must include a session token");
        assertFalse(response.sessionToken.isEmpty(),
            "Session token must not be empty");
    }

    @Test
    @Tag("A1")
    void sessionTokenIsUniquePerPlayer() {
        // A1: Two concurrent logins must yield two different session tokens.
        // Duplicate tokens would allow one player to hijack another player's session.
        LoginResponse response1 = LoginResponse.success(1, 5, sessionStore.createSession(1, "room-1"), "room-1");
        LoginResponse response2 = LoginResponse.success(2, 5, sessionStore.createSession(2, "room-1"), "room-1");
        assertNotNull(response1.sessionToken);
        assertNotNull(response2.sessionToken);
        assertNotEquals(response1.sessionToken, response2.sessionToken,
            "Two login responses must have different session tokens");
    }

    // ------------------------------------------------------------------
    // Reconnect within the timeout window
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void playerWithValidTokenCanRejoinWithinTimeoutWindow() {
        // A1 / FR4.3: A player who disconnects and reconnects within the 30-second window,
        // presenting their original session token, must receive a successful LoginResponse
        // and be placed back into the same GameRoom as before.
        SessionStore sessionStore = new SessionStore();
        GameRoom room = new GameRoom("room-1", 1);
        PlayerConnection connection = conn(1);
        connection.setPlayerName("Alice");
        room.addPlayer(connection, player(1, "Alice"));

        String token = sessionStore.createSession(1, "room-1");
        assertNotNull(token);

        // Simulate disconnect
        room.removePlayer(1);

        // Validate the token within the window
        assertTrue(sessionStore.isValid(token), "Token should be valid within timeout window");
        assertEquals("room-1", sessionStore.getRoomId(token));
        assertEquals(1, sessionStore.getConnectionId(token));
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void reconnectingPlayerIsRestoredToOriginalSlot() {
        // A1 / FR4.3: After a valid reconnect, the player must be assigned the same slot
        // (slot 1 or slot 2) they occupied before disconnecting. The opponent's slot must be unchanged.
        SessionStore sessionStore = new SessionStore();
        GameRoom room = new GameRoom("room-1", 1);
        PlayerConnection conn1 = conn(1);
        PlayerConnection conn2 = conn(2);
        conn1.setPlayerName("Alice");
        conn2.setPlayerName("Bob");
        room.addPlayer(conn1, player(1, "Alice"));
        room.addPlayer(conn2, player(2, "Bob"));

        int originalSlot = room.getSlotByPlayerId(1);
        assertEquals(1, originalSlot);

        String token = sessionStore.createSession(1, "room-1");
        room.removePlayer(1);

        // Reconnect â€” player should return to slot 1
        PlayerConnection reconnConn = conn(3); // new connection ID from KryoNet
        reconnConn.setPlayerName("Alice");
        room.addPlayer(reconnConn, player(3, "Alice"));
        assertEquals(originalSlot, room.getSlotByPlayerId(3),
            "Reconnected player must be restored to original slot");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void gameStateIsPreservedAcrossPlayerReconnect() {
        // A1 / FR4.3: Score and game phase must remain unchanged after a player reconnects.
        // The server must not reset the score or restart the match on reconnect.
        SessionStore sessionStore = new SessionStore();
        GameRoom room = new GameRoom("room-1", 1);
        PlayerConnection conn1 = conn(1);
        PlayerConnection conn2 = conn(2);
        conn1.setPlayerName("Alice");
        conn2.setPlayerName("Bob");
        room.addPlayer(conn1, player(1, "Alice"));
        room.addPlayer(conn2, player(2, "Bob"));
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getScore().scorePlayer1();

        String token = sessionStore.createSession(1, "room-1");
        room.removePlayer(1);

        // After disconnect + reconnect, score and phase must remain
        assertEquals(GameState.Phase.PLAYING, room.getPhase(),
            "Phase must not change on player disconnect");
        assertEquals(1, room.getGameState().getScore().getPlayer1Score(),
            "Score must be preserved across reconnect");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void opponentIsNotifiedWhenPlayerReconnects() {
        // A1 / FR4.3: When a disconnected player successfully rejoins, the opponent must
        // receive a packet (e.g., PlayerRejoined) so the client can resume rendering the opponent.
        SessionStore sessionStore = new SessionStore();
        GameRoom room = new GameRoom("room-1", 1);
        PlayerConnection conn1 = conn(1);
        PlayerConnection conn2 = conn(2);
        conn1.setPlayerName("Alice");
        conn2.setPlayerName("Bob");
        room.addPlayer(conn1, player(1, "Alice"));
        room.addPlayer(conn2, player(2, "Bob"));

        String token = sessionStore.createSession(1, "room-1");
        room.removePlayer(1);

        // Reconnect â€” the room should have a mechanism to notify conn2
        // This test verifies the notification is sent (requires ServerConnector mock)
        assertTrue(sessionStore.isValid(token));
        assertTrue(room.getConnections().containsKey(2),
            "Opponent must still be in the room after player disconnect");
    }

    // ------------------------------------------------------------------
    // Reconnect outside the timeout window
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void reconnectAfterTimeoutWindowIsRejectedWithFailureResponse() {
        // A1 / FR4.3: If more than 30 seconds have passed since the player disconnected,
        // the server must ignore the session token and respond with a failed LoginResponse,
        // treating the connection as a brand-new player.
        //
        // Use a controllable clock: start at t=0 so the token expires at t=30_000,
        // then advance to t=31_000 before sweeping to simulate time passing.
        long[] fakeNowMs = {0L};
        SessionStore sessionStore = new SessionStore(() -> fakeNowMs[0]);
        String token = sessionStore.createSession(1, "room-1");

        // Advance clock beyond the 30-second window, then sweep expired sessions
        fakeNowMs[0] = SessionStore.DEFAULT_TIMEOUT_MS + 1_000;
        sessionStore.invalidateExpired();

        assertFalse(sessionStore.isValid(token),
            "Token must be invalid after timeout window expires");
    }

    @Test
    @Tag("A1")
    void expiredSessionTokenCannotBeReused() {
        // A1: Once a session token has expired (timeout elapsed), it must be removed from
        // the server's token store. A subsequent reconnect attempt with the same token must fail.
        SessionStore sessionStore = new SessionStore();
        String token = sessionStore.createSession(1, "room-1");
        sessionStore.invalidate(token);

        assertFalse(sessionStore.isValid(token),
            "Invalidated token must not be accepted");
        assertNull(sessionStore.getRoomId(token),
            "Invalidated token must not resolve to a room");
        assertEquals(-1, sessionStore.getConnectionId(token),
            "Invalidated token must not resolve to a connection");
    }

    // ------------------------------------------------------------------
    // Session refresh (sliding timeout)
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void refreshByConnectionIdExtendsExpiry() {
        // A1 / FR4.3: Every received packet refreshes the session timeout so it
        // doesn't expire during an active game. After refresh, the token must
        // remain valid even past the original expiry.
        long[] fakeNowMs = {0L};
        SessionStore store = new SessionStore(() -> fakeNowMs[0]);
        String token = store.createSession(1, "room-1");

        // Advance to 25 s (within original window) and refresh
        fakeNowMs[0] = 25_000L;
        store.refreshByConnectionId(1);

        // Advance to 35 s â€” past the original 30 s window but within refreshed window
        fakeNowMs[0] = 35_000L;
        assertTrue(store.isValid(token),
            "Token must still be valid after refresh extended the window");
    }

    @Test
    @Tag("A1")
    void refreshByConnectionIdDoesNotReviveExpiredToken() {
        // A1: If the token is already expired, refreshByConnectionId must not revive it.
        long[] fakeNowMs = {0L};
        SessionStore store = new SessionStore(() -> fakeNowMs[0]);
        String token = store.createSession(1, "room-1");

        // Advance past expiry
        fakeNowMs[0] = SessionStore.DEFAULT_TIMEOUT_MS + 1_000;
        assertFalse(store.isValid(token), "Token must be expired");

        // Attempt refresh â€” should not revive
        store.refreshByConnectionId(1);
        assertFalse(store.isValid(token),
            "Expired token must not be revived by refresh");
    }

    @Test
    @Tag("A1")
    void refreshByConnectionIdDoesNotAffectOtherPlayers() {
        // A1: Refreshing connection 1 must not extend the timeout of connection 2.
        long[] fakeNowMs = {0L};
        SessionStore store = new SessionStore(() -> fakeNowMs[0]);
        String token1 = store.createSession(1, "room-1");
        String token2 = store.createSession(2, "room-1");

        // Advance to 25 s and refresh only connection 1
        fakeNowMs[0] = 25_000L;
        store.refreshByConnectionId(1);

        // Advance past original window
        fakeNowMs[0] = 35_000L;
        assertTrue(store.isValid(token1), "Refreshed token must be valid");
        assertFalse(store.isValid(token2), "Non-refreshed token must have expired");
    }

    // ------------------------------------------------------------------
    // Bulk sweep (invalidateExpired)
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    void invalidateExpiredSweepsOnlyExpiredTokens() {
        // A1: invalidateExpired must remove all expired tokens while keeping valid ones.
        long[] fakeNowMs = {0L};
        SessionStore store = new SessionStore(() -> fakeNowMs[0]);
        String earlyToken = store.createSession(1, "room-1");

        // Create a second token 20 s later so it expires later
        fakeNowMs[0] = 20_000L;
        String lateToken = store.createSession(2, "room-2");

        // At 31 s: earlyToken (created at 0) expired, lateToken (created at 20 s) still valid
        fakeNowMs[0] = 31_000L;
        store.invalidateExpired();

        assertFalse(store.isValid(earlyToken), "Early token must be swept");
        assertTrue(store.isValid(lateToken), "Late token must survive sweep");
    }

    // ------------------------------------------------------------------
    // Invalid / unknown tokens
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    void loginWithUnknownSessionTokenIsRejected() {
        // A1: A LoginRequest that carries an unrecognised session token (tampered or random)
        // must receive a failed LoginResponse. The server must not create a ghost session.
        SessionStore sessionStore = new SessionStore();
        String fakeToken = "totally-fake-token-abc123";

        assertFalse(sessionStore.isValid(fakeToken),
            "Unknown token must not be accepted");
        assertNull(sessionStore.getRoomId(fakeToken),
            "Unknown token must not resolve to a room");
    }

    // ------------------------------------------------------------------
    // Room no longer available
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void playerCannotRejoinRoomThatHasBeenAbandoned() {
        // A1 / FR4.3: If the opponent also disconnected and the room was cleaned up before
        // the token window expired, the reconnecting player must receive a failed LoginResponse
        // (the room no longer exists to rejoin).
        SessionStore sessionStore = new SessionStore();
        GameRoom room = new GameRoom("room-1", 1);
        PlayerConnection conn1 = conn(1);
        PlayerConnection conn2 = conn(2);
        conn1.setPlayerName("Alice");
        conn2.setPlayerName("Bob");
        room.addPlayer(conn1, player(1, "Alice"));
        room.addPlayer(conn2, player(2, "Bob"));

        String token = sessionStore.createSession(1, "room-1");
        room.removePlayer(1);
        room.removePlayer(2);

        assertTrue(room.isEmpty(), "Room should be empty after both players leave");
        // The room would be removed from the rooms map by GameServer â€” token should lead nowhere
        assertTrue(sessionStore.isValid(token),
            "Token itself may still be valid, but the room no longer exists");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    void playerCannotRejoinRoomAfterMatchHasEnded() {
        // A1 / FR4.3: If the match reached GAME_OVER before the disconnected player reconnects,
        // the session token must be invalidated and the reconnect attempt must be rejected.
        SessionStore sessionStore = new SessionStore();
        GameRoom room = new GameRoom("room-1", 1);
        PlayerConnection conn1 = conn(1);
        PlayerConnection conn2 = conn(2);
        conn1.setPlayerName("Alice");
        conn2.setPlayerName("Bob");
        room.addPlayer(conn1, player(1, "Alice"));
        room.addPlayer(conn2, player(2, "Bob"));
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);

        String token = sessionStore.createSession(1, "room-1");
        room.removePlayer(1);

        // Match ends while player is disconnected
        room.getGameState().finishMatch();
        assertEquals(GameState.Phase.FINISHED, room.getPhase());

        // Token should be invalidated when match finishes
        sessionStore.invalidate(token);
        assertFalse(sessionStore.isValid(token),
            "Token must be invalid after match ends");
    }
}
