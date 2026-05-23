package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.LoginRequest;
import no.ntnu.ping404.network.packets.LoginResponse;
import no.ntnu.ping404.network.packets.PlayerJoined;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoginHandlerCommand reconnect/reattach logic (issue #38).
 *
 * <p>Requirements covered: A1 (availability), FR4.3 (disconnect/reconnect handling).
 */
class LoginHandlerReconnectTest {

    private RecordingServerConnector connector;
    private StubNetworkServer networkServer;
    private Map<String, GameRoom> rooms;
    private Map<Integer, GameRoom> playerRooms;
    private SessionStore sessionStore;
    private LoginHandlerCommand handler;

    @BeforeEach
    void setUp() {
        networkServer = new StubNetworkServer();
        connector = new RecordingServerConnector();
        rooms = new ConcurrentHashMap<>();
        playerRooms = new ConcurrentHashMap<>();
        sessionStore = new SessionStore();
        handler = new LoginHandlerCommand(connector, rooms, playerRooms, sessionStore);
    }

    // ------------------------------------------------------------------
    // Successful reconnect
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Player with valid token reconnects to original room")
    void validTokenReconnectsToOriginalRoom() {
        // Login player 1 (creates room)
        PlayerConnection conn1 = conn(1);
        handler.handle(conn1, loginRequest("Alice"));
        assertEquals(1, rooms.size());
        GameRoom room = rooms.values().iterator().next();
        String roomId = room.getRoomId();

        // Get token from the login response
        LoginResponse loginResp = connector.getLastPacketOfType(1, LoginResponse.class);
        assertNotNull(loginResp);
        String token = loginResp.sessionToken;
        assertNotNull(token);

        // Simulate disconnect
        room.removePlayer(1);
        playerRooms.remove(1);

        // Reconnect with token using new connection ID
        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnectRequest = new LoginRequest("Alice");
        reconnectRequest.sessionToken = token;
        handler.handle(reconnConn, reconnectRequest);

        // Verify success
        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp, "Reconnecting player must receive a LoginResponse");
        assertTrue(reconnResp.success, "Reconnect with valid token must succeed");
        assertNotNull(reconnResp.sessionToken, "Reconnect response must include a new session token");

        // Verify reattached to same room
        assertTrue(room.getConnections().containsKey(10),
            "Reconnected player must be in the original room");
        assertEquals(room, playerRooms.get(10),
            "playerRooms must map new connection to original room");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Reconnected player gets restored to original slot")
    void reconnectedPlayerRestoredToOriginalSlot() {
        // Login two players
        handler.handle(conn(1), loginRequest("Alice"));
        handler.handle(conn(2), loginRequest("Bob"));
        GameRoom room = rooms.values().iterator().next();

        LoginResponse aliceResp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = aliceResp.sessionToken;

        int originalSlot = room.getSlotByPlayerId(1);
        assertEquals(1, originalSlot);

        // Disconnect Alice
        room.removePlayer(1);
        playerRooms.remove(1);

        // Reconnect Alice with new connection
        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        assertEquals(originalSlot, room.getSlotByPlayerId(10),
            "Reconnected player must be restored to their original slot");
        assertEquals(2, room.getSlotByPlayerId(2),
            "Opponent slot must be unchanged");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Old token is invalidated after successful reconnect")
    void oldTokenInvalidatedAfterReconnect() {
        handler.handle(conn(1), loginRequest("Alice"));
        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = resp.sessionToken;
        GameRoom room = rooms.values().iterator().next();

        room.removePlayer(1);
        playerRooms.remove(1);

        // Reconnect
        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        // Old token must be consumed
        assertFalse(sessionStore.isValid(token),
            "Old session token must be invalidated after successful reconnect");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Fresh token is issued on successful reconnect")
    void freshTokenIssuedOnReconnect() {
        handler.handle(conn(1), loginRequest("Alice"));
        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        String oldToken = resp.sessionToken;
        GameRoom room = rooms.values().iterator().next();

        room.removePlayer(1);
        playerRooms.remove(1);

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = oldToken;
        handler.handle(reconnConn, reconnReq);

        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp.sessionToken);
        assertNotEquals(oldToken, reconnResp.sessionToken,
            "Reconnect must issue a fresh token, not reuse the old one");
        assertTrue(sessionStore.isValid(reconnResp.sessionToken),
            "New token must be valid after reconnect");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Opponent is notified when a player reconnects")
    void opponentNotifiedOnReconnect() {
        handler.handle(conn(1), loginRequest("Alice"));
        handler.handle(conn(2), loginRequest("Bob"));
        GameRoom room = rooms.values().iterator().next();
        LoginResponse aliceResp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = aliceResp.sessionToken;

        room.removePlayer(1);
        playerRooms.remove(1);
        connector.clear();

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        // Bob (connection 2) should have received a PlayerJoined notification
        PlayerJoined notification = connector.getLastPacketOfType(2, PlayerJoined.class);
        assertNotNull(notification,
            "Opponent must receive PlayerJoined when player reconnects");
        assertEquals("Alice", notification.playerName);
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Game state is preserved across reconnect")
    void gameStatePreservedAcrossReconnect() {
        handler.handle(conn(1), loginRequest("Alice"));
        handler.handle(conn(2), loginRequest("Bob"));
        GameRoom room = rooms.values().iterator().next();

        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        room.getGameState().getScore().scorePlayer1();
        room.getGameState().getScore().scorePlayer1();

        LoginResponse aliceResp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = aliceResp.sessionToken;

        room.removePlayer(1);
        playerRooms.remove(1);

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        assertEquals(GameState.Phase.PLAYING, room.getPhase(),
            "Phase must not change after reconnect");
        assertEquals(2, room.getGameState().getScore().getPlayer1Score(),
            "Score must be preserved after reconnect");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Player can reconnect twice using fresh token each time")
    void doubleReconnectSucceeds() {
        handler.handle(conn(1), loginRequest("Alice"));
        GameRoom room = rooms.values().iterator().next();
        LoginResponse resp1 = connector.getLastPacketOfType(1, LoginResponse.class);
        String token1 = resp1.sessionToken;

        // First disconnect + reconnect
        room.removePlayer(1);
        playerRooms.remove(1);

        PlayerConnection reconn1 = conn(10);
        LoginRequest req1 = new LoginRequest("Alice");
        req1.sessionToken = token1;
        handler.handle(reconn1, req1);

        LoginResponse resp2 = connector.getLastPacketOfType(10, LoginResponse.class);
        assertTrue(resp2.success);
        String token2 = resp2.sessionToken;
        assertNotEquals(token1, token2, "Second token must differ from first");

        // Second disconnect + reconnect with the fresh token
        room.removePlayer(10);
        playerRooms.remove(10);

        PlayerConnection reconn2 = conn(20);
        LoginRequest req2 = new LoginRequest("Alice");
        req2.sessionToken = token2;
        handler.handle(reconn2, req2);

        LoginResponse resp3 = connector.getLastPacketOfType(20, LoginResponse.class);
        assertNotNull(resp3);
        assertTrue(resp3.success, "Second reconnect with fresh token must succeed");
        assertTrue(room.getConnections().containsKey(20));
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Reconnect during PAUSED phase succeeds")
    void reconnectDuringPausedPhaseSucceeds() {
        handler.handle(conn(1), loginRequest("Alice"));
        handler.handle(conn(2), loginRequest("Bob"));
        GameRoom room = rooms.values().iterator().next();
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PAUSED);

        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = resp.sessionToken;

        room.removePlayer(1);
        playerRooms.remove(1);

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp);
        assertTrue(reconnResp.success, "Reconnect during PAUSED phase must succeed");
        assertEquals(GameState.Phase.PAUSED, room.getPhase());
    }

    @Test
    @Tag("A1")
    @DisplayName("Normal login works when sessionToken is null")
    void normalLoginWorksWithNullToken() {
        // Regression guard: the reconnect branch must not break the normal login path
        LoginRequest request = loginRequest("Alice");
        assertNull(request.sessionToken, "Default sessionToken must be null");

        handler.handle(conn(1), request);

        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        assertNotNull(resp);
        assertTrue(resp.success, "Normal login with null token must succeed");
        assertNotNull(resp.sessionToken, "Normal login must issue a session token");
        assertEquals(1, rooms.size(), "A room must be created for the player");
    }

    @Test
    @Tag("A1")
    @DisplayName("Empty sessionToken falls through to normal login")
    void emptyTokenFallsThroughToNormalLogin() {
        LoginRequest request = loginRequest("Alice");
        request.sessionToken = "";

        handler.handle(conn(1), request);

        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        assertNotNull(resp);
        assertTrue(resp.success, "Empty-string token must fall through to normal login");
        assertEquals(1, rooms.size(), "A room must be created for the player");
    }

    // ------------------------------------------------------------------
    // Rejected reconnect
    // ------------------------------------------------------------------

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Expired token is rejected with failure response")
    void expiredTokenRejected() {
        handler.handle(conn(1), loginRequest("Alice"));
        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = resp.sessionToken;
        GameRoom room = rooms.values().iterator().next();

        room.removePlayer(1);
        playerRooms.remove(1);

        // Simulate token expiry by explicitly invalidating it
        sessionStore.invalidate(token);

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp);
        assertFalse(reconnResp.success, "Expired token must be rejected");
    }

    @Test
    @Tag("A1")
    @DisplayName("Unknown token is rejected with failure response")
    void unknownTokenRejected() {
        PlayerConnection conn = conn(1);
        LoginRequest request = new LoginRequest("Alice");
        request.sessionToken = "totally-fake-token-abc123";
        handler.handle(conn, request);

        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        assertNotNull(resp);
        assertFalse(resp.success, "Unknown session token must be rejected");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Token for removed room is rejected")
    void tokenForRemovedRoomRejected() {
        handler.handle(conn(1), loginRequest("Alice"));
        LoginResponse resp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = resp.sessionToken;
        GameRoom room = rooms.values().iterator().next();
        String roomId = room.getRoomId();

        // Simulate both players leaving and room being cleaned up
        room.removePlayer(1);
        playerRooms.remove(1);
        rooms.remove(roomId);

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp);
        assertFalse(reconnResp.success, "Token for a removed room must be rejected");
        assertFalse(sessionStore.isValid(token), "Token must be invalidated when room is gone");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Token for full room is rejected")
    void tokenForFullRoomRejected() {
        handler.handle(conn(1), loginRequest("Alice"));
        LoginResponse aliceResp = connector.getLastPacketOfType(1, LoginResponse.class);
        String aliceToken = aliceResp.sessionToken;

        handler.handle(conn(2), loginRequest("Bob"));
        GameRoom room = rooms.values().iterator().next();

        // Alice disconnects
        room.removePlayer(1);
        playerRooms.remove(1);

        // Third player joins and fills the room
        handler.handle(conn(3), loginRequest("Charlie"));
        assertTrue(room.isFull(), "Room must be full after replacement player joins");

        // Alice tries to reconnect
        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = aliceToken;
        handler.handle(reconnConn, reconnReq);

        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp);
        assertFalse(reconnResp.success, "Token for full room must be rejected");
    }

    @Test
    @Tag("A1")
    @Tag("FR4.3")
    @DisplayName("Token for FINISHED room is rejected")
    void tokenForGameOverRoomRejected() {
        handler.handle(conn(1), loginRequest("Alice"));
        handler.handle(conn(2), loginRequest("Bob"));
        GameRoom room = rooms.values().iterator().next();
        LoginResponse aliceResp = connector.getLastPacketOfType(1, LoginResponse.class);
        String token = aliceResp.sessionToken;

        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.FINISHED);
        room.removePlayer(1);
        playerRooms.remove(1);

        PlayerConnection reconnConn = conn(10);
        LoginRequest reconnReq = new LoginRequest("Alice");
        reconnReq.sessionToken = token;
        handler.handle(reconnConn, reconnReq);

        LoginResponse reconnResp = connector.getLastPacketOfType(10, LoginResponse.class);
        assertNotNull(reconnResp);
        assertFalse(reconnResp.success, "Reconnect to FINISHED room must be rejected");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PlayerConnection conn(int id) {
        return new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
    }

    private static LoginRequest loginRequest(String name) {
        return new LoginRequest(name, "1.0.0");
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    private static class StubNetworkServer extends NetworkKryoServer {
        @Override
        public int getConnectionCount() {
            return 0;
        }

        @Override
        public void forEachConnection(java.util.function.BiConsumer<Integer, INetworkServer.PlayerConnection> consumer) {
        }
    }

    /**
     * Records all packets sent to each connection, allowing assertions on what
     * was sent to whom.
     */
    private static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            sentPackets.add(new SentPacket(connectionId, packet));
        }

        @Override
        public void send(PlayerConnection connection, Object packet) {
            sentPackets.add(new SentPacket(connection.getId(), packet));
        }

        void clear() {
            sentPackets.clear();
        }

        /**
         * Returns the last packet of the given type sent to the given connection,
         * or null if none was sent.
         */
        @SuppressWarnings("unchecked")
        <T> T getLastPacketOfType(int connectionId, Class<T> type) {
            for (int i = sentPackets.size() - 1; i >= 0; i--) {
                SentPacket sp = sentPackets.get(i);
                if (sp.connectionId == connectionId && type.isInstance(sp.packet)) {
                    return (T) sp.packet;
                }
            }
            return null;
        }

        private record SentPacket(int connectionId, Object packet) {}
    }
}
