package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.*;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.server.handler.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Network room-isolation tests. */
class RoomIsolationTest {

    // Connection IDs used across tests
    private static final int ALICE_ID = 1;
    private static final int BOB_ID = 2;
    private static final int CHARLIE_ID = 3;
    private static final int DIANA_ID = 4;
    private static final int EVE_ID = 5;
    private static final int FRANK_ID = 6;
    private static final int UNREGISTERED_ID = 999;

    // Player names
    private static final String ALICE = "Alice";
    private static final String BOB = "Bob";
    private static final String CHARLIE = "Charlie";
    private static final String DIANA = "Diana";
    private static final String EVE = "Eve";
    private static final String FRANK = "Frank";

    // Coordinates for position updates
    private static final float POSITION_X = 100f;
    private static final float POSITION_Y = 200f;
    private static final float POSITION_X_ALT = 150f;
    private static final float POSITION_Y_ALT = 250f;

    // ------------------------------------------------------------------
    // Test Double: RecordingServerConnector
    // ------------------------------------------------------------------

    private static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector(NetworkKryoServer networkServer) {
            super(networkServer);
        }

        @Override
        public void send(int connectionId, Object packet) {
            String channel = (packet instanceof PlayerPosition
                    || packet instanceof Ping
                    || packet instanceof Pong)
                    ? "UDP"
                    : "TCP";
            sentPackets.add(new SentPacket(connectionId, packet, channel));
        }

        @Override
        public void send(PlayerConnection connection, Object packet) {
            String channel = (packet instanceof PlayerPosition
                    || packet instanceof Ping
                    || packet instanceof Pong)
                    ? "UDP"
                    : "TCP";
            sentPackets.add(new SentPacket(connection.getId(), packet, channel));
        }

        @Override
        public void broadcast(Object packet) {
            String channel = (packet instanceof PlayerPosition
                    || packet instanceof Ping
                    || packet instanceof Pong)
                    ? "UDP"
                    : "TCP";
            // Record as violation: broadcast should not be used for player-facing messages
            sentPackets.add(new SentPacket(-1, packet, channel + "_GLOBAL_VIOLATION"));
        }

        @Override
        public void broadcastExcept(int excludeConnectionId, Object packet) {
            String channel = (packet instanceof PlayerPosition
                    || packet instanceof Ping
                    || packet instanceof Pong)
                    ? "UDP"
                    : "TCP";
            sentPackets.add(new SentPacket(excludeConnectionId, packet, channel + "_BROADCAST_EXCEPT"));
        }

        List<SentPacket> getPackets() {
            return new ArrayList<>(sentPackets);
        }

        List<Object> getPacketsSentTo(int connectionId) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .toList();
        }

        <T> List<T> getPacketsSentToOfType(int connectionId, Class<T> type) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        <T> List<T> getAllPacketsOfType(Class<T> type) {
            return sentPackets.stream()
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        void clear() {
            sentPackets.clear();
        }

        boolean hasGlobalBroadcastViolation() {
            return sentPackets.stream()
                    .anyMatch(p -> p.channel.contains("GLOBAL_VIOLATION"));
        }

        record SentPacket(int connectionId, Object packet, String channel) {}
    }

    // ------------------------------------------------------------------
    // Test Double: RecordingNetworkServer (for packet routing)
    // ------------------------------------------------------------------

    private static class RecordingNetworkServer extends NetworkKryoServer {
        final Map<Integer, INetworkServer.PlayerConnection> connections = new ConcurrentHashMap<>();

        void registerConnection(int connectionId, INetworkServer.PlayerConnection conn) {
            connections.put(connectionId, conn);
        }

        @Override
        public int getConnectionCount() {
            return connections.size();
        }

        @Override
        public void forEachConnection(java.util.function.BiConsumer<Integer, INetworkServer.PlayerConnection> action) {
            connections.forEach(action);
        }
    }

    // ------------------------------------------------------------------
    // Base Test Setup
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
        sessionStore = new SessionStore(); // Initialize the session store
        loginHandler = new LoginHandlerCommand(connector, rooms, playerRooms, sessionStore);
    }

    private PlayerConnection createConnection(int id) {
        PlayerConnection existing = networkServer.connections.get(id);
        if (existing != null) {
            return existing;
        }
        PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
        networkServer.registerConnection(id, conn);
        return conn;
    }

    private void loginPlayer(int connectionId, String playerName) {
        PlayerConnection conn = createConnection(connectionId);
        LoginRequest request = new LoginRequest();
        request.playerName = playerName;
        loginHandler.handle(conn, request);
    }

    // ------------------------------------------------------------------
    // TESTS: Chat Message Room Isolation (Issue #24 Primary Focus)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Chat Message Room Isolation")
    @Tag("FR1.5")
    class ChatMessageRoomIsolation {

        @Test
        @DisplayName("Chat message sent by Player A in Room 1 reaches only Room 1 members")
        void chatMessageRoomScoped() {
            // Arrange: Create two rooms with two players each
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);    // Room 1: Alice, Bob

            connector.clear();

            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);  // Room 2: Charlie, Diana

            connector.clear();

            // Alice sends chat message
            PlayerConnection aliceConn = createConnection(ALICE_ID);
            ChatMessage chatMsg = new ChatMessage();
            chatMsg.senderName = ALICE;
            chatMsg.message = "Hello from Room 1";

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);
            chatHandler.handle(aliceConn, chatMsg);

            // Assert: Chat message reaches Bob (Room 1) but NOT Charlie/Diana (Room 2)
            List<ChatMessage> bobReceived = connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class);
            List<ChatMessage> charlieReceived = connector.getPacketsSentToOfType(CHARLIE_ID, ChatMessage.class);
            List<ChatMessage> dianaReceived = connector.getPacketsSentToOfType(DIANA_ID, ChatMessage.class);

            assertEquals(1, bobReceived.size(), "Bob should receive chat message from Alice");
            assertTrue(charlieReceived.isEmpty(), "Charlie should NOT receive chat from different room");
            assertTrue(dianaReceived.isEmpty(), "Diana should NOT receive chat from different room");
            assertFalse(connector.hasGlobalBroadcastViolation(), "Chat should NOT use global broadcast");
        }

        @Test
        @DisplayName("Chat message sender receives their own message")
        void chatMessageSenderReceivesOwnMessage() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);

            connector.clear();

            PlayerConnection aliceConn = createConnection(ALICE_ID);
            ChatMessage chatMsg = new ChatMessage();
            chatMsg.senderName = ALICE;
            chatMsg.message = "My message";

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);
            chatHandler.handle(aliceConn, chatMsg);

            // Assert: Both Alice and Bob receive the message
            List<ChatMessage> aliceReceived = connector.getPacketsSentToOfType(ALICE_ID, ChatMessage.class);
            List<ChatMessage> bobReceived = connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class);

            assertEquals(1, aliceReceived.size(), "Alice should receive her own message");
            assertEquals(1, bobReceived.size(), "Bob should receive Alice's message");
        }

        @Test
        @DisplayName("Multiple chat messages in sequence maintain room isolation")
        void multipleChatsRoomIsolated() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);

            // Act: Send multiple messages from room 1 and room 2
            ChatMessage msg1 = new ChatMessage();
            msg1.senderName = ALICE;
            msg1.message = "Message 1";
            chatHandler.handle(createConnection(ALICE_ID), msg1);

            ChatMessage msg2 = new ChatMessage();
            msg2.senderName = CHARLIE;
            msg2.message = "Message 2";
            chatHandler.handle(createConnection(CHARLIE_ID), msg2);

            // Assert
            List<ChatMessage> bobMessages = connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class);
            List<ChatMessage> dianaMessages = connector.getPacketsSentToOfType(DIANA_ID, ChatMessage.class);

            assertEquals(1, bobMessages.size(), "Bob should receive only Alice's message");
            assertEquals(1, dianaMessages.size(), "Diana should receive only Charlie's message");
            assertEquals(ALICE, bobMessages.get(0).senderName);
            assertEquals(CHARLIE, dianaMessages.get(0).senderName);
        }
    }

    // ------------------------------------------------------------------
    // TESTS: System Notifications Room Isolation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("System Notifications Room Isolation")
    @Tag("FR1.5")
    class SystemNotificationsRoomIsolation {

        @Test
        @DisplayName("PlayerJoined notification is sent only to room members")
        void playerJoinedNotificationRoomScoped() {
            // Arrange: create two rooms
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            
            loginPlayer(CHARLIE_ID, CHARLIE);
            connector.clear();

            // Act: Diana joins Charlie's room (Room 2)
            loginPlayer(DIANA_ID, DIANA);

            // Assert
            List<PlayerJoined> bobNotifications = connector.getPacketsSentToOfType(BOB_ID, PlayerJoined.class);
            List<PlayerJoined> charlieNotifications = connector.getPacketsSentToOfType(CHARLIE_ID, PlayerJoined.class);

            assertTrue(charlieNotifications.stream()
                .anyMatch(pj -> pj.playerName.equals(DIANA)),
                "Charlie should be notified that Diana joined his room");
            assertTrue(bobNotifications.isEmpty(),
                "Bob should NOT be notified of join in a different room");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }

        @Test
        @DisplayName("PlayerLeft notification is sent only to remaining room member")
        void playerLeftNotificationRoomScoped() {
            // Arrange: Two players in Room 1, two players in Room 2
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            // Act: Alice disconnects
            GameRoom room1 = playerRooms.get(ALICE_ID);
            assertNotNull(room1, "Alice should be in a room");

            PlayerDisconnectHandler disconnectHandler = new PlayerDisconnectHandler(connector, new SessionStore());
            disconnectHandler.handlePlayerDisconnect(1, room1);
            room1.removePlayer(1);

            // Assert: Bob (Room 1) gets PlayerLeft, Charlie/Diana (Room 2) do not
            List<PlayerLeft> bobNotifications = connector.getPacketsSentToOfType(BOB_ID, PlayerLeft.class);
            List<PlayerLeft> charlieNotifications = connector.getPacketsSentToOfType(CHARLIE_ID, PlayerLeft.class);
            List<PlayerLeft> dianaNotifications = connector.getPacketsSentToOfType(DIANA_ID, PlayerLeft.class);

            assertEquals(1, bobNotifications.size(), "Bob should receive PlayerLeft notification");
            assertEquals(ALICE, bobNotifications.get(0).playerName);
            assertTrue(charlieNotifications.isEmpty(), "Charlie should NOT receive PlayerLeft from different room");
            assertTrue(dianaNotifications.isEmpty(), "Diana should NOT receive PlayerLeft from different room");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }
    }

    // ------------------------------------------------------------------
    // TESTS: Game State Updates Room Isolation
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Game State Updates Room Isolation")
    @Tag("TC6")
    class GameStateUpdatesRoomIsolation {

        @Test
        @DisplayName("PlayerPosition updates are sent only to room members")
        void positionUpdatesRoomScoped() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            GameRoom room1 = playerRooms.get(ALICE_ID);
            GameRoom room2 = playerRooms.get(CHARLIE_ID);
            assertNotNull(room1);
            assertNotNull(room2);
            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PLAYING);
            room2.setPhase(GameState.Phase.PLAYING);
            room2.setPhase(GameState.Phase.PLAYING);

            connector.clear();

            // Act: Alice sends position update
            PlayerConnection aliceConn = createConnection(ALICE_ID);
            PlayerPosition posUpdate = new PlayerPosition(ALICE_ID, POSITION_X, POSITION_Y);

            PositionHandlerCommand posHandler = new PositionHandlerCommand(connector, playerRooms, null);
            posHandler.handle(aliceConn, posUpdate);

            // Assert: Bob gets update, Charlie/Diana do not
            List<PlayerPosition> bobUpdates = connector.getPacketsSentToOfType(BOB_ID, PlayerPosition.class);
            List<PlayerPosition> charlieUpdates = connector.getPacketsSentToOfType(CHARLIE_ID, PlayerPosition.class);
            List<PlayerPosition> dianaUpdates = connector.getPacketsSentToOfType(DIANA_ID, PlayerPosition.class);

            assertEquals(1, bobUpdates.size(), "Bob should receive position update from Alice");
            assertTrue(charlieUpdates.isEmpty(), "Charlie should not receive updates from other room");
            assertTrue(dianaUpdates.isEmpty(), "Diana should not receive updates from other room");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }

        @Test
        @DisplayName("Position sender does NOT receive their own position update")
        void positionSenderExcludedFromBroadcast() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);

            GameRoom room1 = playerRooms.get(ALICE_ID);
            assertNotNull(room1);
            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PLAYING);

            connector.clear();

            // Act: Alice sends position update
            PlayerConnection aliceConn = createConnection(ALICE_ID);
            PlayerPosition posUpdate = new PlayerPosition(ALICE_ID, POSITION_X_ALT, POSITION_Y_ALT);

            PositionHandlerCommand posHandler = new PositionHandlerCommand(connector, playerRooms, null);
            posHandler.handle(aliceConn, posUpdate);

            // Assert: Alice should NOT receive her own position update, Bob should
            List<PlayerPosition> aliceUpdates = connector.getPacketsSentToOfType(ALICE_ID, PlayerPosition.class);
            List<PlayerPosition> bobUpdates = connector.getPacketsSentToOfType(BOB_ID, PlayerPosition.class);

            assertTrue(aliceUpdates.isEmpty(), "Sender should NOT receive their own position update");
            assertEquals(1, bobUpdates.size(), "Bob should receive Alice's position update");
            assertEquals(POSITION_X_ALT, bobUpdates.get(0).x);
            assertEquals(POSITION_Y_ALT, bobUpdates.get(0).y);
        }

        @Test
        @DisplayName("ResumeRequest/ResumeEvent are room-scoped")
        void resumeEventsRoomScoped() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            GameRoom room1 = playerRooms.get(ALICE_ID);
            assertNotNull(room1);
            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PAUSED);

            // Act: Alice resumes game in Room 1
            PlayerConnection aliceConn = createConnection(ALICE_ID);
            ResumeRequest resumeReq = new ResumeRequest();

            ResumeHandlerCommand resumeHandler = new ResumeHandlerCommand(connector, playerRooms, null);
            resumeHandler.handle(aliceConn, resumeReq);

            // Assert: Bob gets resume event, Charlie/Diana do not
            List<ResumeEvent> bobEvents = connector.getPacketsSentToOfType(BOB_ID, ResumeEvent.class);
            List<ResumeEvent> charlieEvents = connector.getPacketsSentToOfType(CHARLIE_ID, ResumeEvent.class);

            assertEquals(1, bobEvents.size(), "Bob should receive resume event from Room 1");
            assertTrue(charlieEvents.isEmpty(), "Charlie should not receive resume from different room");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }

        @Test
        @DisplayName("Pong handler updates heartbeat, does not broadcast")
        void pongUpdatesHeartbeatOnly() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);

            connector.clear();

            // Act: Alice sends pong
            PlayerConnection aliceConn = createConnection(ALICE_ID);
            long beforeHeartbeat = aliceConn.getLastHeartbeatTime();
            Pong pong = new Pong();

            PongHandlerCommand pongHandler = new PongHandlerCommand();
            pongHandler.handle(aliceConn, pong);

            // Assert: Heartbeat was updated, no packets sent (Pong is server-side bookkeeping)
            assertTrue(aliceConn.getLastHeartbeatTime() >= beforeHeartbeat,
                    "Alice's heartbeat timestamp should be updated");
            assertTrue(connector.getPackets().isEmpty(),
                    "PongHandler should not send any packets - it only updates heartbeat");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }

        @Test
        @DisplayName("Ping response is point-to-point, not broadcast")
        void pingResponsePointToPoint() {
            // Arrange: Two rooms
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            // Act: Alice sends Ping
            PlayerConnection aliceConn = createConnection(ALICE_ID);
            Ping ping = new Ping();

            PingHandlerCommand pingHandler = new PingHandlerCommand(connector);
            pingHandler.handle(aliceConn, ping);

            // Assert: Only Alice receives the Pong response (point-to-point)
            List<Pong> alicePongs = connector.getPacketsSentToOfType(ALICE_ID, Pong.class);
            List<Pong> bobPongs = connector.getPacketsSentToOfType(BOB_ID, Pong.class);
            List<Pong> charliePongs = connector.getPacketsSentToOfType(CHARLIE_ID, Pong.class);
            List<Pong> dianaPongs = connector.getPacketsSentToOfType(DIANA_ID, Pong.class);

            assertEquals(1, alicePongs.size(), "Alice should receive exactly one Pong response");
            assertTrue(bobPongs.isEmpty(), "Bob should not receive Alice's Pong");
            assertTrue(charliePongs.isEmpty(), "Charlie should not receive Alice's Pong");
            assertTrue(dianaPongs.isEmpty(), "Diana should not receive Alice's Pong");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }

    }

    // ------------------------------------------------------------------
    // TESTS: Concurrent Room Independence
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrent Room Independence")
    @Tag("TC5")
    class ConcurrentRoomIndependence {

        @Test
        @DisplayName("Messages in 3+ concurrent rooms do not interfere")
        void multipleRoomsIndependent() {
            // Arrange: Create 3 rooms with 2 players each
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);      // Room 1: Alice, Bob

            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);    // Room 2: Charlie, Diana

            loginPlayer(EVE_ID, EVE);
            loginPlayer(FRANK_ID, FRANK);    // Room 3: Eve, Frank

            connector.clear();

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);

            // Act: Each room sends a chat message
            ChatMessage msg1 = new ChatMessage();
            msg1.senderName = ALICE;
            msg1.message = "Room 1 chat";
            chatHandler.handle(createConnection(ALICE_ID), msg1);

            ChatMessage msg2 = new ChatMessage();
            msg2.senderName = CHARLIE;
            msg2.message = "Room 2 chat";
            chatHandler.handle(createConnection(CHARLIE_ID), msg2);

            ChatMessage msg3 = new ChatMessage();
            msg3.senderName = EVE;
            msg3.message = "Room 3 chat";
            chatHandler.handle(createConnection(EVE_ID), msg3);

            // Assert: Each player receives only messages from their room
            List<ChatMessage> bobMsgs = connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class);
            List<ChatMessage> dianaMsgs = connector.getPacketsSentToOfType(DIANA_ID, ChatMessage.class);
            List<ChatMessage> frankMsgs = connector.getPacketsSentToOfType(FRANK_ID, ChatMessage.class);

            assertEquals(1, bobMsgs.size(), "Bob should receive only Room 1 chat");
            assertEquals(ALICE, bobMsgs.get(0).senderName);

            assertEquals(1, dianaMsgs.size(), "Diana should receive only Room 2 chat");
            assertEquals(CHARLIE, dianaMsgs.get(0).senderName);

            assertEquals(1, frankMsgs.size(), "Frank should receive only Room 3 chat");
            assertEquals(EVE, frankMsgs.get(0).senderName);
        }

        @Test
        @DisplayName("Message ordering is maintained per room")
        void messageOrderingPerRoom() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);

            // Act: Send multiple messages in sequence
            for (int i = 1; i <= 3; i++) {
                ChatMessage msg = new ChatMessage();
                msg.senderName = ALICE;
                msg.message = "Message " + i;
                chatHandler.handle(createConnection(ALICE_ID), msg);
            }

            // Assert: Bob receives messages in order
            List<ChatMessage> bobMsgs = connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class);
            assertEquals(3, bobMsgs.size());
            assertEquals("Message 1", bobMsgs.get(0).message);
            assertEquals("Message 2", bobMsgs.get(1).message);
            assertEquals("Message 3", bobMsgs.get(2).message);
        }
    }

    // ------------------------------------------------------------------
    // TESTS: Broadcast Violation Detection (ensures no global broadcast used)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Broadcast Violation Detection")
    @Tag("FR1.5")
    class BroadcastViolationDetection {

        @Test
        @DisplayName("Chat handler does NOT use global broadcast")
        void chatHandlerNoGlobalBroadcast() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);

            connector.clear();

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);
            ChatMessage msg = new ChatMessage();
            msg.senderName = ALICE;
            msg.message = "Test";

            // Act
            chatHandler.handle(createConnection(ALICE_ID), msg);

            // Assert: No global broadcast violations
            assertFalse(connector.hasGlobalBroadcastViolation(),
                    "ChatHandlerCommand must not use connector.broadcast()");
        }

        @Test
        @DisplayName("PlayerJoined handler does NOT use global broadcast")
        void playerJoinedHandlerNoGlobalBroadcast() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            connector.clear();

            // Act: Bob joins
            loginPlayer(BOB_ID, BOB);

            // Assert
            assertFalse(connector.hasGlobalBroadcastViolation(),
                    "PlayerJoined must not use global broadcast");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    @Tag("TC6")
    class EdgeCaseHandling {

        @Test
        @DisplayName("Handler gracefully handles player not in any room")
        void playerNotInRoomHandledGracefully() {
            // Arrange: Create a connection that's not registered in any room
            PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(UNREGISTERED_ID);
            networkServer.registerConnection(UNREGISTERED_ID, conn);

            ChatMessage msg = new ChatMessage();
            msg.senderName = "Unknown";
            msg.message = "Test";

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);

            // Act & Assert: Should not crash
            assertDoesNotThrow(() -> chatHandler.handle(conn, msg),
                    "Handler should gracefully handle player not in room");
        }

        @Test
        @DisplayName("Rapid position updates in same room stay isolated")
        void rapidPositionUpdatesIsolated() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            GameRoom room1 = playerRooms.get(ALICE_ID);
            GameRoom room2 = playerRooms.get(CHARLIE_ID);
            assertNotNull(room1);
            assertNotNull(room2);
            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PLAYING);
            room2.setPhase(GameState.Phase.PLAYING);
            room2.setPhase(GameState.Phase.PLAYING);

            connector.clear();

            PositionHandlerCommand posHandler = new PositionHandlerCommand(connector, playerRooms, null);

            // Act: Send 10 rapid position updates from Alice
            for (int i = 0; i < 10; i++) {
                PlayerPosition pos = new PlayerPosition(ALICE_ID, POSITION_X + i, POSITION_Y + i);
                posHandler.handle(createConnection(ALICE_ID), pos);
            }

            // Assert: Bob receives all 10, Charlie/Diana receive none
            List<PlayerPosition> bobUpdates = connector.getPacketsSentToOfType(BOB_ID, PlayerPosition.class);
            List<PlayerPosition> charlieUpdates = connector.getPacketsSentToOfType(CHARLIE_ID, PlayerPosition.class);
            List<PlayerPosition> dianaUpdates = connector.getPacketsSentToOfType(DIANA_ID, PlayerPosition.class);

            assertEquals(10, bobUpdates.size(), "Bob should receive all 10 position updates");
            assertTrue(charlieUpdates.isEmpty(), "Charlie should receive no updates from other room");
            assertTrue(dianaUpdates.isEmpty(), "Diana should receive no updates from other room");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }

        @Test
        @DisplayName("Mixed message types in same room remain isolated")
        void mixedMessageTypesIsolated() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            GameRoom room1 = playerRooms.get(ALICE_ID);
            assertNotNull(room1);
            room1.setPhase(GameState.Phase.PLAYING);
            room1.setPhase(GameState.Phase.PLAYING);

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);
            PositionHandlerCommand posHandler = new PositionHandlerCommand(connector, playerRooms, null);
            PauseHandlerCommand pauseHandler = new PauseHandlerCommand(connector, playerRooms, null);

            // Act: Send different message types
            ChatMessage chat = new ChatMessage();
            chat.senderName = ALICE;
            chat.message = "Room 1 chat";
            chatHandler.handle(createConnection(ALICE_ID), chat);

            PlayerPosition pos = new PlayerPosition(ALICE_ID, POSITION_X, POSITION_Y);
            posHandler.handle(createConnection(ALICE_ID), pos);

            PauseRequest pause = new PauseRequest();
            pauseHandler.handle(createConnection(ALICE_ID), pause);

            // Assert: Bob gets all three, others get none
            assertEquals(1, connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class).size());
            assertEquals(1, connector.getPacketsSentToOfType(BOB_ID, PlayerPosition.class).size());
            assertEquals(1, connector.getPacketsSentToOfType(BOB_ID, PauseEvent.class).size());

            assertTrue(connector.getPacketsSentToOfType(CHARLIE_ID, ChatMessage.class).isEmpty());
            assertTrue(connector.getPacketsSentToOfType(CHARLIE_ID, PlayerPosition.class).isEmpty());
            assertTrue(connector.getPacketsSentToOfType(CHARLIE_ID, PauseEvent.class).isEmpty());

            assertFalse(connector.hasGlobalBroadcastViolation());
        }

    }

    // ------------------------------------------------------------------
    // TESTS: Login Response Point-to-Point Verification
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Login Response Point-to-Point Delivery")
    @Tag("FR1.5")
    class LoginResponsePointToPointDelivery {

        @Test
        @DisplayName("Login response is sent only to requesting player")
        void loginResponsePointToPoint() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            connector.clear();

            // Create connections for two new login requests
            PlayerConnection conn1 = createConnection(CHARLIE_ID);
            PlayerConnection conn2 = createConnection(DIANA_ID);

            LoginRequest loginReq1 = new LoginRequest();
            loginReq1.playerName = CHARLIE;
            LoginRequest loginReq2 = new LoginRequest();
            loginReq2.playerName = DIANA;

            LoginHandlerCommand loginHandler = new LoginHandlerCommand(connector, rooms, playerRooms, sessionStore);

            // Act: Handle two login requests
            loginHandler.handle(conn1, loginReq1);
            loginHandler.handle(conn2, loginReq2);

            // Assert: Each player gets response ONLY to their own connection
            List<LoginResponse> conn1Responses = connector.getPacketsSentToOfType(CHARLIE_ID, LoginResponse.class);
            List<LoginResponse> conn2Responses = connector.getPacketsSentToOfType(DIANA_ID, LoginResponse.class);
            List<LoginResponse> aliceResponses = connector.getPacketsSentToOfType(ALICE_ID, LoginResponse.class);

            assertEquals(1, conn1Responses.size(), "Connection 3 should receive login response");
            assertEquals(1, conn2Responses.size(), "Connection 4 should receive login response");
            assertTrue(aliceResponses.isEmpty(), "Alice should not receive other player's login response");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }
    }

    // ------------------------------------------------------------------
    // TESTS: System Message Scoping
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("System Message Scoping")
    @Tag("FR1.5")
    class SystemMessageScoping {

        @Test
        @DisplayName("System messages are room-scoped")
        void systemMessagesRoomScoped() {
            // Arrange
            loginPlayer(ALICE_ID, ALICE);
            loginPlayer(BOB_ID, BOB);
            loginPlayer(CHARLIE_ID, CHARLIE);
            loginPlayer(DIANA_ID, DIANA);

            connector.clear();

            ChatHandlerCommand chatHandler = new ChatHandlerCommand(connector, playerRooms);

            // Act: Send a system-type message to Room 1
            ChatMessage sysMsg = new ChatMessage();
            sysMsg.senderName = "SYSTEM";
            sysMsg.message = "Game starting in 10 seconds";
            sysMsg.type = ChatMessage.MessageType.SYSTEM;

            chatHandler.handle(createConnection(ALICE_ID), sysMsg);

            // Assert: Only Room 1 members receive system message
            List<ChatMessage> bobMsgs = connector.getPacketsSentToOfType(BOB_ID, ChatMessage.class);
            List<ChatMessage> dianaMsgs = connector.getPacketsSentToOfType(DIANA_ID, ChatMessage.class);

            assertEquals(1, bobMsgs.size(), "Bob should receive system message from Room 1");
            assertTrue(dianaMsgs.isEmpty(), "Diana should not receive system message from other room");
            assertFalse(connector.hasGlobalBroadcastViolation());
        }
    }
}

