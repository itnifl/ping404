package no.ntnu.ping404.server.handler;

import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.LoginRequest;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.SessionStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LoginHandlerCommand, focusing on win score configuration (FR1.6, M3).
 */
class LoginHandlerCommandTestForWinScore {

    private static final int HOST_CONN_ID = 1;
    private static final int GUEST_CONN_ID = 2;
    private static final String HOST_NAME = "Host";
    private static final String GUEST_NAME = "Guest";
    private static final String VERSION = "1.0.0";
    private static final int DEFAULT_WIN_SCORE = 5;
    private static final int WIN_SCORE_CUSTOM = 10;
    private static final int WIN_SCORE_NEGATIVE = -5;
    private static final int WIN_SCORE_HOST = 7;
    private static final int WIN_SCORE_GUEST = 15;
    private static final int WIN_SCORE_MIN = 1;
    private static final int WIN_SCORE_LARGE = 100;
    private static final int WIN_SCORE_GAME_STATE = 12;

    private RecordingServerConnector connector;
    private StubNetworkServer networkServer;
    private Map<String, GameRoom> rooms;
    private Map<Integer, GameRoom> playerRooms;
    private LoginHandlerCommand handler;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        GameConfig.reset(); // Ensure default config state
        networkServer = new StubNetworkServer();
        connector = new RecordingServerConnector();
        rooms = new ConcurrentHashMap<>();
        playerRooms = new ConcurrentHashMap<>();
        sessionStore = new SessionStore(); // Initialize the session store
        handler = new LoginHandlerCommand(connector, rooms, playerRooms, sessionStore);
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Room uses default win score (5) when not specified in login request")
    void roomUsesDefaultWinScoreWhenNotSpecified() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME);
        // winScore is null
        
        handler.handle(conn, request);
        
        assertEquals(1, rooms.size(), "Should create one room");
        GameRoom room = rooms.values().iterator().next();
        assertEquals(DEFAULT_WIN_SCORE, room.getWinScore(), "Should use default win score of 5");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Room uses custom win score when specified by host")
    void roomUsesCustomWinScoreWhenSpecified() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME, VERSION, WIN_SCORE_CUSTOM);
        
        handler.handle(conn, request);
        
        assertEquals(1, rooms.size(), "Should create one room");
        GameRoom room = rooms.values().iterator().next();
        assertEquals(WIN_SCORE_CUSTOM, room.getWinScore(), "Should use custom win score of 10");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Win score of zero falls back to configured default")
    void winScoreZeroUsesDefault() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME, VERSION, 0);
        
        handler.handle(conn, request);
        
        GameRoom room = rooms.values().iterator().next();
        assertEquals(DEFAULT_WIN_SCORE, room.getWinScore(), "Win score of 0 should fall back to configured default");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Negative win score falls back to configured default")
    void negativeWinScoreUsesDefault() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME, VERSION, WIN_SCORE_NEGATIVE);
        
        handler.handle(conn, request);
        
        GameRoom room = rooms.values().iterator().next();
        assertEquals(DEFAULT_WIN_SCORE, room.getWinScore(), "Negative win score should fall back to configured default");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Non-host player's win score is ignored when joining existing room")
    void nonHostWinScoreIsIgnored() {
        // Host creates room with win score 7
        PlayerConnection host = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest hostRequest = new LoginRequest(HOST_NAME, VERSION, WIN_SCORE_HOST);
        handler.handle(host, hostRequest);
        
        // Non-host joins with different win score
        PlayerConnection guest = createPlayerConnection(GUEST_CONN_ID, null);
        LoginRequest guestRequest = new LoginRequest(GUEST_NAME, VERSION, WIN_SCORE_GUEST);
        handler.handle(guest, guestRequest);
        
        // Should still have only one room
        assertEquals(1, rooms.size(), "Guest should join existing room, not create new one");
        GameRoom room = rooms.values().iterator().next();
        assertEquals(WIN_SCORE_HOST, room.getWinScore(), "Room should keep host's win score, not guest's");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Win score of 1 is accepted")
    void winScoreOfOneIsAccepted() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME, VERSION, WIN_SCORE_MIN);
        
        handler.handle(conn, request);
        
        GameRoom room = rooms.values().iterator().next();
        assertEquals(WIN_SCORE_MIN, room.getWinScore(), "Win score of 1 should be accepted");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Large win score values are accepted")
    void largeWinScoreIsAccepted() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME, VERSION, WIN_SCORE_LARGE);
        
        handler.handle(conn, request);
        
        GameRoom room = rooms.values().iterator().next();
        assertEquals(WIN_SCORE_LARGE, room.getWinScore(), "Large win score should be accepted");
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Game state score uses room's configured win score")
    void gameStateScoreUsesRoomWinScore() {
        PlayerConnection conn = createPlayerConnection(HOST_CONN_ID, null);
        LoginRequest request = new LoginRequest(HOST_NAME, VERSION, WIN_SCORE_GAME_STATE);
        
        handler.handle(conn, request);
        
        GameRoom room = rooms.values().iterator().next();
        assertEquals(WIN_SCORE_GAME_STATE, room.getGameState().getScore().getWinningScore(),
                "GameState Score should use room's configured win score");
    }

    /* 
        Helper methods
    */

    private PlayerConnection createPlayerConnection(int id, String name) {
        PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
        if (name != null) {
            conn.setPlayerName(name);
        }
        return conn;
    }

    /*
        Test doubles
    */

    private static class StubNetworkServer extends NetworkKryoServer {
        @Override
        public int getConnectionCount() {
            return 0;
        }

        @Override
        public void forEachConnection(java.util.function.BiConsumer<Integer, INetworkServer.PlayerConnection> consumer) {
            // No existing connections by default
        }
    }

    private static class RecordingServerConnector extends ServerConnector {
        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            // No-op: tests only verify room state, not sent packets
        }
    }
}
