package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.*;
import no.ntnu.ping404.server.handler.LoginHandlerCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GameRoom functionality that involve handler interactions,
 * room assignment logic, and room-scoped event delivery.
 */
class GameRoomIntegrationTests {

    // -- Test constants: connection IDs --
    private static final int CONN_ID_1 = 1;
    private static final int CONN_ID_2 = 2;
    private static final int CONN_ID_3 = 3;
    private static final int CONN_ID_4 = 4;
    private static final int CONN_ID_5 = 5;
    private static final int CONN_ID_10 = 10;
    private static final int CONN_ID_20 = 20;
    private static final int CONN_ID_42 = 42;

    // -- Test constants: player names --
    private static final String PLAYER_ALICE = "Alice";
    private static final String PLAYER_BOB = "Bob";
    private static final String PLAYER_CHARLIE = "Charlie";
    private static final String PLAYER_DIANA = "Diana";
    private static final String PLAYER_TEST = "TestPlayer";
    private static final String INVALID_EMPTY_NAME = "";

    record SentPacket(int connectionId, Object packet, String channel) {}

    // ------------------------------------------------------------------
    // Recording test doubles
    // ------------------------------------------------------------------

    static class RecordingNetworkServer extends NetworkKryoServer {
        private final Map<Integer, INetworkServer.PlayerConnection> connections = new ConcurrentHashMap<>();
        private int connectionCount = 0;

        @Override
        public int getConnectionCount() {
            return connectionCount;
        }

        public void setConnectionCount(int count) {
            this.connectionCount = count;
        }

        public void registerConnection(INetworkServer.PlayerConnection conn) {
            connections.put(conn.getId(), conn);
            connectionCount = connections.size();
        }

        @Override
        public void forEachConnection(java.util.function.BiConsumer<Integer, INetworkServer.PlayerConnection> action) {
            connections.forEach(action);
        }
    }

    static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector(NetworkKryoServer networkServer) {
            super(networkServer);
        }

        @Override
        public void send(int connectionId, Object packet) {
            String channel = packet instanceof PlayerPosition
                    || packet instanceof Ping
                    || packet instanceof Pong
                    ? "UDP"
                    : "TCP";
            sentPackets.add(new SentPacket(connectionId, packet, channel));
        }

        @Override
        public void send(PlayerConnection connection, Object packet) {
            String channel = packet instanceof PlayerPosition
                    || packet instanceof Ping
                    || packet instanceof Pong
                    ? "UDP"
                    : "TCP";
            sentPackets.add(new SentPacket(connection.getId(), packet, channel));
        }

        public List<SentPacket> getSentPackets() {
            return sentPackets;
        }

        public List<Object> getPacketsSentTo(int connectionId) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .toList();
        }

        public <T> List<T> getPacketsSentToOfType(int connectionId, Class<T> type) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        public <T> List<T> getAllPacketsOfType(Class<T> type) {
            return sentPackets.stream()
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        public void clearPackets() {
            sentPackets.clear();
        }
    }

    // ------------------------------------------------------------------
    // Test fixtures
    // ------------------------------------------------------------------

    private RecordingNetworkServer networkServer;
    private RecordingServerConnector connector;
    private Map<String, GameRoom> rooms;
    private Map<Integer, GameRoom> playerRooms;
    private LoginHandlerCommand loginHandler;
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        networkServer = new RecordingNetworkServer();
        connector = new RecordingServerConnector(networkServer);
        rooms = new ConcurrentHashMap<>();
        playerRooms = new ConcurrentHashMap<>();
        sessionStore = new SessionStore(); 
        loginHandler = new LoginHandlerCommand(connector, rooms, playerRooms, sessionStore);
    }

    private PlayerConnection createConnection(int id, String name) {
        PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
        conn.setPlayerName(name);
        networkServer.registerConnection(conn);
        return conn;
    }

    private void loginPlayer(int connectionId, String playerName) {
        PlayerConnection conn = createConnection(connectionId, null); // name set by handler
        LoginRequest request = new LoginRequest();
        request.playerName = playerName;
        loginHandler.handle(conn, request);
    }

    // ------------------------------------------------------------------
    // FR1.3 â€” Room joining logic
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("FR1.3 - Room Joining Logic")
    class RoomJoiningLogic {

        @Test
        @Tag("FR1.3")
        @DisplayName("First player creates a new room")
        void firstPlayerCreatesNewRoom() {
            // FR1.3: First login should create a new room and assign the player.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);

            assertEquals(1, rooms.size(), "One room should be created");
            assertEquals(1, playerRooms.size(), "Player should be in playerRooms");

            GameRoom room = playerRooms.get(CONN_ID_1);
            assertNotNull(room);
            assertEquals(1, room.getPlayerCount());
            assertEquals(CONN_ID_1, room.getHostConnectionId(), "First player should be host");
        }

        @Test
        @Tag("FR1.3")
        @DisplayName("Second player joins existing WAITING room")
        void secondPlayerJoinsExistingWaitingRoom() {
            // FR1.3: Second player should join the existing room, not create a new one.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);

            assertEquals(1, rooms.size(), "Should still be one room");
            assertEquals(2, playerRooms.size(), "Both players should be mapped");

            GameRoom aliceRoom = playerRooms.get(CONN_ID_1);
            GameRoom bobRoom = playerRooms.get(CONN_ID_2);
            
            assertSame(aliceRoom, bobRoom, "Both players should be in the same room");
            assertEquals(2, aliceRoom.getPlayerCount());
            assertTrue(aliceRoom.isFull());
            assertTrue(aliceRoom.canStart());
        }

        @Test
        @Tag("FR1.3")
        @Tag("FR1.4")
        @DisplayName("Third player gets a new room when existing room is full")
        void thirdPlayerGetsNewRoomWhenExistingIsFull() {
            // FR1.3/FR1.4: When a room is full, the next player must get a new room.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);

            assertEquals(2, rooms.size(), "Two rooms should exist");
            assertEquals(3, playerRooms.size(), "All three players should be mapped");

            GameRoom room1 = playerRooms.get(CONN_ID_1);
            GameRoom room2 = playerRooms.get(CONN_ID_3);
            
            assertNotSame(room1, room2, "Charlie should be in a different room");
            assertSame(room1, playerRooms.get(CONN_ID_2), "Alice and Bob should be in the same room");
            
            assertTrue(room1.isFull(), "Room 1 should be full");
            assertFalse(room2.isFull(), "Room 2 should not be full");
        }

        @Test
        @Tag("FR1.3")
        @DisplayName("Rooms have unique IDs")
        void roomsHaveUniqueIds() {
            // FR1.3: Each room must have a unique identifier.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);
            loginPlayer(CONN_ID_4, PLAYER_DIANA);

            assertEquals(2, rooms.size());
            
            List<String> roomIds = rooms.keySet().stream().toList();
            assertEquals(2, roomIds.stream().distinct().count(), "All room IDs should be unique");
        }
    }

    // ------------------------------------------------------------------
    // FR4.3 / FR4.5 â€” Room-scoped events
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("FR4.3/FR4.5 - Room-Scoped Events")
    class RoomScopedEvents {

        @Test
        @Tag("FR4.3")
        @DisplayName("PlayerJoined is only sent to room members")
        void playerJoinedOnlySentToRoomMembers() {
            // FR4.3: PlayerJoined notification must only go to players in the same room.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);  // Joins Alice's room
            
            connector.clearPackets();
            
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);  // Creates new room
            loginPlayer(CONN_ID_4, PLAYER_DIANA);    // Joins Charlie's room

            // Diana's join should notify Charlie only, not Alice or Bob
            List<PlayerJoined> joinNotifications = connector.getAllPacketsOfType(PlayerJoined.class);
            
            // Should have PlayerJoined sent to Charlie (for Diana joining)
                boolean charlieSawDianaJoin = connector.getPacketsSentToOfType(CONN_ID_3, PlayerJoined.class)
                    .stream()
                    .anyMatch(pj -> pj.playerName.equals(PLAYER_DIANA));
            assertTrue(charlieSawDianaJoin, "Charlie should see Diana join");

            // Neither Alice nor Bob should see Diana join
                assertTrue(connector.getPacketsSentToOfType(CONN_ID_1, PlayerJoined.class).isEmpty(),
                    "Alice should not see Diana join");
                assertTrue(connector.getPacketsSentToOfType(CONN_ID_2, PlayerJoined.class).isEmpty(),
                    "Bob should not see Diana join");
        }

        @Test
        @Tag("FR4.5")
        @DisplayName("Player list contains only same-room players")
        void playerListContainsOnlySameRoomPlayers() {
            // FR4.5: PlayerList sent on login should contain only players from the same room.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            connector.clearPackets();
            
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);
            
            // Charlie's PlayerList should be empty (new room, no other players yet)
            List<PlayerList> charliePlayerLists = connector.getPacketsSentToOfType(CONN_ID_3, PlayerList.class);
            assertEquals(1, charliePlayerLists.size());
            
            PlayerList charlieList = charliePlayerLists.get(0);
            assertEquals(0, charlieList.players.size(), 
                    "Charlie should see no other players (alone in new room)");
        }

        @Test
        @Tag("FR4.5")
        @DisplayName("Second player sees first player in PlayerList")
        void secondPlayerSeesFirstPlayerInPlayerList() {
            // FR4.5: When joining a room with another player, PlayerList should contain them.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            connector.clearPackets();
            
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            
            List<PlayerList> bobPlayerLists = connector.getPacketsSentToOfType(CONN_ID_2, PlayerList.class);
            assertEquals(1, bobPlayerLists.size());
            
            PlayerList bobList = bobPlayerLists.get(0);
            assertEquals(1, bobList.players.size(), "Bob should see Alice");
            assertEquals(PLAYER_ALICE, bobList.players.get(0).playerName);
        }
    }

    // ------------------------------------------------------------------
    // FR4.1/FR4.2 â€” Host tracking in handler flow
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("FR4.1/FR4.2 - Host Tracking")
    class HostTracking {

        @Test
        @Tag("FR4.1")
        @DisplayName("Room host is the first player who joins")
        void roomHostIsFirstPlayerWhoJoins() {
            // FR4.1: The connection ID of the first player becomes the host.
            loginPlayer(CONN_ID_5, PLAYER_ALICE);

            GameRoom room = playerRooms.get(CONN_ID_5);
            assertEquals(CONN_ID_5, room.getHostConnectionId(), "Host should be connection 5");
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Host remains after second player joins")
        void hostRemainsAfterSecondPlayerJoins() {
            // FR4.2: Host ID should not change when the second player joins.
            loginPlayer(CONN_ID_10, PLAYER_ALICE);
            loginPlayer(CONN_ID_20, PLAYER_BOB);

            GameRoom room = playerRooms.get(CONN_ID_10);
            assertEquals(CONN_ID_10, room.getHostConnectionId(), "Host should still be 10");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("New room has new host")
        void newRoomHasNewHost() {
            // FR4.1: When a new room is created, the first player in that room is the host.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);

            GameRoom room1 = playerRooms.get(CONN_ID_1);
            GameRoom room2 = playerRooms.get(CONN_ID_3);

            assertEquals(CONN_ID_1, room1.getHostConnectionId(), "Room 1 host should be player 1");
            assertEquals(CONN_ID_3, room2.getHostConnectionId(), "Room 2 host should be player 3");
        }
    }

    // ------------------------------------------------------------------
    // Room isolation (logical, not concurrency-based)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Room Isolation")
    class RoomIsolation {

        @Test
        @Tag("FR4.3")
        @DisplayName("Actions in one room do not affect another room's state")
        void actionsInOneRoomDoNotAffectAnother() {
            // FR4.3: State changes (like status) in one room should not affect other rooms.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);
            loginPlayer(CONN_ID_4, PLAYER_DIANA);

            GameRoom room1 = playerRooms.get(CONN_ID_1);
            GameRoom room2 = playerRooms.get(CONN_ID_3);

            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PLAYING);

            assertEquals(GameState.Phase.PLAYING, room1.getPhase());
            assertEquals(GameState.Phase.WAITING, room2.getPhase(), 
                    "Room 2 status should be unaffected");
        }

        @Test
        @Tag("FR4.3")
        @DisplayName("Each room has independent GameState")
        void eachRoomHasIndependentGameState() {
            // FR4.3: Each room's GameState must be separate.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);

            GameRoom room1 = playerRooms.get(CONN_ID_1);
            GameRoom room2 = playerRooms.get(CONN_ID_3);

            assertNotSame(room1.getGameState(), room2.getGameState(),
                    "Rooms should have different GameState instances");

            assertEquals(2, room1.getGameState().getPlayerCount());
            assertEquals(1, room2.getGameState().getPlayerCount());
        }

        @Test
        @Tag("FR4.3")
        @DisplayName("Player removal in one room does not affect another")
        void playerRemovalInOneRoomDoesNotAffectAnother() {
            // FR4.3: Removing a player from one room should not change other rooms.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            loginPlayer(CONN_ID_2, PLAYER_BOB);
            loginPlayer(CONN_ID_3, PLAYER_CHARLIE);
            loginPlayer(CONN_ID_4, PLAYER_DIANA);

            GameRoom room1 = playerRooms.get(CONN_ID_1);
            GameRoom room2 = playerRooms.get(CONN_ID_3);

            room1.removePlayer(CONN_ID_1);

            assertEquals(1, room1.getPlayerCount(), "Room 1 should have 1 player");
            assertEquals(2, room2.getPlayerCount(), "Room 2 should still have 2 players");
        }
    }

    // ------------------------------------------------------------------
    // Login validation handling
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Login Validation")
    class LoginValidation {

        @Test
        @Tag("FR4.5")
        @DisplayName("Login with invalid name sends failure response")
        void loginWithInvalidNameSendsFailure() {
            // FR4.5: Invalid player names should result in LoginResponse.failure().
            PlayerConnection conn = createConnection(CONN_ID_1, null);
            LoginRequest request = new LoginRequest();
            request.playerName = INVALID_EMPTY_NAME;  // Invalid
            
            loginHandler.handle(conn, request);

            List<LoginResponse> responses = connector.getPacketsSentToOfType(CONN_ID_1, LoginResponse.class);
            assertEquals(1, responses.size());
            assertFalse(responses.get(0).success, "Login should fail for empty name");
            assertTrue(rooms.isEmpty(), "No room should be created on failure");
        }

        @Test
        @Tag("FR4.5")
        @DisplayName("Login with duplicate name sends failure response")
        void loginWithDuplicateNameSendsFailure() {
            // FR4.5: Duplicate player names should be rejected.
            loginPlayer(CONN_ID_1, PLAYER_ALICE);
            connector.clearPackets();
            
            PlayerConnection conn2 = createConnection(CONN_ID_2, null);
            LoginRequest duplicateRequest = new LoginRequest();
            duplicateRequest.playerName = PLAYER_ALICE;
            
            loginHandler.handle(conn2, duplicateRequest);

            List<LoginResponse> responses = connector.getPacketsSentToOfType(CONN_ID_2, LoginResponse.class);
            assertEquals(1, responses.size());
            assertFalse(responses.get(0).success, "Login should fail for duplicate name");
        }

        @Test
        @Tag("FR4.5")
        @DisplayName("Successful login sends success response with connection ID")
        void successfulLoginSendsSuccessResponse() {
            // FR4.5: Successful login should return success with the connection ID.
            PlayerConnection conn = createConnection(CONN_ID_42, null);
            LoginRequest request = new LoginRequest();
            request.playerName = PLAYER_TEST;
            
            loginHandler.handle(conn, request);

            List<LoginResponse> responses = connector.getPacketsSentToOfType(CONN_ID_42, LoginResponse.class);
            assertEquals(1, responses.size());
            assertTrue(responses.get(0).success);
            assertEquals(CONN_ID_42, responses.get(0).playerId);
        }
    }

    // ------------------------------------------------------------------
    // Integration: Full game start scenario
    // ------------------------------------------------------------------

    @Test
    @Tag("FR1.4")
    @DisplayName("Full scenario: Two players login and room becomes startable")
    void fullScenarioTwoPlayersLoginAndRoomBecomesStartable() {
        // FR1.4: End-to-end scenario verifying room readiness.
        
        // Player 1 logs in
        loginPlayer(CONN_ID_1, PLAYER_ALICE);
        
        GameRoom room = playerRooms.get(CONN_ID_1);
        assertNotNull(room);
        assertFalse(room.canStart(), "Room should not be startable with 1 player");
        assertEquals(GameState.Phase.WAITING, room.getPhase());
        
        // Player 2 logs in
        loginPlayer(CONN_ID_2, PLAYER_BOB);
        
        assertSame(room, playerRooms.get(CONN_ID_2), "Bob should join Alice's room");
        assertTrue(room.canStart(), "Room should be startable with 2 players");
        assertTrue(room.isFull());
        assertEquals(GameState.Phase.WAITING, room.getPhase());
        
        // Verify both players received correct packets
        assertFalse(connector.getPacketsSentToOfType(CONN_ID_1, LoginResponse.class).isEmpty());
        assertFalse(connector.getPacketsSentToOfType(CONN_ID_2, LoginResponse.class).isEmpty());
        
        // Alice should have received PlayerJoined for Bob
        List<PlayerJoined> aliceJoinNotifications = connector.getPacketsSentToOfType(CONN_ID_1, PlayerJoined.class);
        assertEquals(1, aliceJoinNotifications.size());
        assertEquals(PLAYER_BOB, aliceJoinNotifications.get(0).playerName);
    }
}

