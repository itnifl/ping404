package no.ntnu.ping404.server.handler;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.network.packets.GameStartRequest;
import no.ntnu.ping404.server.GameRoom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GameStartHandlerCommand - authoritative game start flow.
 *
 * Policy under test:
 * - Only the host may start the game.
 * - Room must be full (2 players) and in WAITING phase.
 * - On valid request, broadcasts GameStartEvent to both players with correct slot info.
 * - On invalid request, sends ErrorPacket to requester.
 */
class GameStartHandlerCommandTest {

    private static final int HOST_ID = 1;
    private static final int GUEST_ID = 2;
    private static final String HOST_NAME = "Alice";
    private static final String GUEST_NAME = "Bob";
    private static final String ROOM_ID = "TEST123";
    private static final int WIN_SCORE = 5;

    /** Recording test double for ServerConnector. */
    private static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            sentPackets.add(new SentPacket(connectionId, packet));
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

        void clear() { sentPackets.clear(); }

        private record SentPacket(int connectionId, Object packet) {}
    }

    /** KryoNet Connection with controlled ID. */
    private static class TestConnection extends Connection {
        private final int id;
        TestConnection(int id) { this.id = id; }
        @Override public int getID() { return id; }
    }

    private RecordingServerConnector connector;
    private Map<Integer, GameRoom> playerRooms;
    private GameStartHandlerCommand handler;

    @BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        playerRooms = new ConcurrentHashMap<>();
        handler = new GameStartHandlerCommand(connector, playerRooms, room -> {});
    }

    private INetworkServer.PlayerConnection asPlayerConnection(int id, String name) {
        return new NetworkKryoServer.KryoPlayerConnection(new TestConnection(id)) {
            @Override
            public String getPlayerName() {
                return name;
            }
        };
    }

    private GameRoom createRoomWithPlayers() {
        GameRoom room = new GameRoom(ROOM_ID, HOST_ID, WIN_SCORE);
        room.addPlayer(asPlayerConnection(HOST_ID, HOST_NAME), new Player(HOST_ID, HOST_NAME));
        room.addPlayer(asPlayerConnection(GUEST_ID, GUEST_NAME), new Player(GUEST_ID, GUEST_NAME));
        playerRooms.put(HOST_ID, room);
        playerRooms.put(GUEST_ID, room);
        return room;
    }

    @Nested
    @DisplayName("Host starts game")
    class HostStartsGame {

        @Test
        @Tag("FR2.1")
        @DisplayName("Host can start when room is full and in WAITING phase")
        void hostStartsGame_roomFullWaiting_broadcastsGameStartEvent() {
            GameRoom room = createRoomWithPlayers();
            assertEquals(GameState.Phase.WAITING, room.getPhase());
            assertTrue(room.canStart());

            handler.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            assertEquals(GameState.Phase.PLAYING, room.getPhase());

            var eventsToHost = connector.getPacketsSentToOfType(HOST_ID, GameStartEvent.class);
            var eventsToGuest = connector.getPacketsSentToOfType(GUEST_ID, GameStartEvent.class);

            assertEquals(1, eventsToHost.size(), "Host should receive GameStartEvent");
            assertEquals(1, eventsToGuest.size(), "Guest should receive GameStartEvent");

            GameStartEvent hostEvent = eventsToHost.get(0);
            assertEquals(HOST_ID, hostEvent.playerId);
            assertEquals(GUEST_ID, hostEvent.opponentId);
            assertEquals(1, hostEvent.playerSlot, "Host is slot 1");
            assertEquals(HOST_NAME, hostEvent.playerName);
            assertEquals(GUEST_NAME, hostEvent.opponentName);
            assertEquals(WIN_SCORE, hostEvent.winScore);

            GameStartEvent guestEvent = eventsToGuest.get(0);
            assertEquals(GUEST_ID, guestEvent.playerId);
            assertEquals(HOST_ID, guestEvent.opponentId);
            assertEquals(2, guestEvent.playerSlot, "Guest is slot 2");
            assertEquals(GUEST_NAME, guestEvent.playerName);
            assertEquals(HOST_NAME, guestEvent.opponentName);
            assertEquals(WIN_SCORE, guestEvent.winScore);
        }

        @Test
        @DisplayName("No ErrorPacket sent on successful start")
        void hostStartsGame_success_noErrorPacket() {
            createRoomWithPlayers();
            handler.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            var errors = connector.getAllPacketsOfType(ErrorPacket.class);
            assertTrue(errors.isEmpty(), "No errors should be sent on successful start");
        }
    }

    @Nested
    @DisplayName("Non-host start rejected")
    class NonHostStartRejected {

        @Test
        @Tag("FR2.1")
        @DisplayName("Guest cannot start the game - ErrorPacket sent")
        void guestStartsGame_rejected_errorPacket() {
            GameRoom room = createRoomWithPlayers();
            assertEquals(GameState.Phase.WAITING, room.getPhase());

            handler.handle(asPlayerConnection(GUEST_ID, GUEST_NAME), new GameStartRequest(GUEST_ID));

            // Phase should remain WAITING
            assertEquals(GameState.Phase.WAITING, room.getPhase());

            // Guest should receive ErrorPacket
            var errors = connector.getPacketsSentToOfType(GUEST_ID, ErrorPacket.class);
            assertEquals(1, errors.size(), "Guest should receive ErrorPacket");
            assertTrue(errors.get(0).message.contains("host"), "Error should mention host");

            // No GameStartEvent should be broadcast
            var events = connector.getAllPacketsOfType(GameStartEvent.class);
            assertTrue(events.isEmpty(), "No GameStartEvent should be broadcast");
        }
    }

    @Nested
    @DisplayName("Room not ready")
    class RoomNotReady {

        @Test
        @DisplayName("Cannot start with only one player")
        void onlyOnePlayer_cannotStart() {
            GameRoom room = new GameRoom(ROOM_ID, HOST_ID, WIN_SCORE);
            room.addPlayer(asPlayerConnection(HOST_ID, HOST_NAME), new Player(HOST_ID, HOST_NAME));
            playerRooms.put(HOST_ID, room);

            handler.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            assertEquals(GameState.Phase.WAITING, room.getPhase());

            var errors = connector.getPacketsSentToOfType(HOST_ID, ErrorPacket.class);
            assertEquals(1, errors.size(), "Should receive error about waiting for opponent");

            var events = connector.getAllPacketsOfType(GameStartEvent.class);
            assertTrue(events.isEmpty());
        }

        @Test
        @DisplayName("Cannot start if already PLAYING")
        void alreadyPlaying_cannotStart() {
            GameRoom room = createRoomWithPlayers();
            room.setPhase(GameState.Phase.PLAYING);

            handler.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            var errors = connector.getPacketsSentToOfType(HOST_ID, ErrorPacket.class);
            assertEquals(1, errors.size());

            // Only 1 GameStartEvent should be present from setPhase, not from handler
            connector.clear();
            handler.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            var events = connector.getAllPacketsOfType(GameStartEvent.class);
            assertTrue(events.isEmpty(), "No new GameStartEvent from rejected request");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Request from unknown connection is ignored")
        void unknownConnection_ignored() {
            createRoomWithPlayers();
            int unknownId = 999;

            handler.handle(asPlayerConnection(unknownId, "Unknown"), new GameStartRequest(unknownId));

            var events = connector.getAllPacketsOfType(GameStartEvent.class);
            var errors = connector.getAllPacketsOfType(ErrorPacket.class);

            assertTrue(events.isEmpty());
            assertTrue(errors.isEmpty(), "Should silently ignore unknown connection");
        }
    }

    @Nested
    @DisplayName("Game start callback")
    class GameStartCallback {

        @Test
        @DisplayName("Callback is invoked when game starts successfully")
        void callbackInvokedOnSuccessfulStart() {
            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            AtomicReference<GameRoom> callbackRoom = new AtomicReference<>();
            
            GameStartHandlerCommand handlerWithCallback = new GameStartHandlerCommand(
                connector, playerRooms, room -> {
                    callbackInvoked.set(true);
                    callbackRoom.set(room);
                });
            
            GameRoom room = createRoomWithPlayers();
            handlerWithCallback.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            assertTrue(callbackInvoked.get(), "Callback should be invoked on successful start");
            assertSame(room, callbackRoom.get(), "Callback should receive the started room");
        }

        @Test
        @DisplayName("Callback is NOT invoked when non-host tries to start")
        void callbackNotInvokedOnNonHostStart() {
            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            
            GameStartHandlerCommand handlerWithCallback = new GameStartHandlerCommand(
                connector, playerRooms, room -> callbackInvoked.set(true));
            
            createRoomWithPlayers();
            handlerWithCallback.handle(asPlayerConnection(GUEST_ID, GUEST_NAME), new GameStartRequest(GUEST_ID));

            assertFalse(callbackInvoked.get(), "Callback should NOT be invoked when non-host tries to start");
        }

        @Test
        @DisplayName("Callback is NOT invoked when room is not full")
        void callbackNotInvokedOnIncompleteRoom() {
            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            
            GameStartHandlerCommand handlerWithCallback = new GameStartHandlerCommand(
                connector, playerRooms, room -> callbackInvoked.set(true));
            
            // Create room with only host (not full)
            GameRoom room = new GameRoom(ROOM_ID, HOST_ID, WIN_SCORE);
            room.addPlayer(asPlayerConnection(HOST_ID, HOST_NAME), new Player(HOST_ID, HOST_NAME));
            playerRooms.put(HOST_ID, room);
            
            handlerWithCallback.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            assertFalse(callbackInvoked.get(), "Callback should NOT be invoked when room is not full");
        }

        @Test
        @DisplayName("Callback is NOT invoked when room is not in WAITING phase")
        void callbackNotInvokedOnWrongPhase() {
            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            
            GameStartHandlerCommand handlerWithCallback = new GameStartHandlerCommand(
                connector, playerRooms, room -> callbackInvoked.set(true));
            
            GameRoom room = createRoomWithPlayers();
            room.setPhase(GameState.Phase.PLAYING); // Already playing
            
            handlerWithCallback.handle(asPlayerConnection(HOST_ID, HOST_NAME), new GameStartRequest(HOST_ID));

            assertFalse(callbackInvoked.get(), "Callback should NOT be invoked when room is not in WAITING phase");
        }
    }
}
