package no.ntnu.ping404.server.handler;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.PauseEvent;
import no.ntnu.ping404.network.packets.PauseRequest;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PauseHandlerCommand â€” issue #29: Authorize pause/resume calls.
 *
 * Policy under test: Only the host (first player to join the room) may pause.
 * Non-host pause requests must be rejected with an ErrorPacket and must NOT
 * broadcast a PauseEvent or change the room phase.
 */
class PauseHandlerCommandTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final int HOST_ID = 1;
    private static final int GUEST_ID = 2;
    private static final String HOST_NAME = "Alice";
    private static final String GUEST_NAME = "Bob";

    // ------------------------------------------------------------------
    // Test Double: RecordingServerConnector
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Test Double: TestConnection (KryoNet Connection with controlled ID)
    // ------------------------------------------------------------------

    private static class TestConnection extends Connection {
        private final int id;
        TestConnection(int id) { this.id = id; }
        @Override public int getID() { return id; }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RecordingServerConnector connector;
    private Map<Integer, GameRoom> playerRooms;
    private PauseHandlerCommand handler;

    @BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        playerRooms = new ConcurrentHashMap<>();
        handler = new PauseHandlerCommand(connector, playerRooms, null);
    }

    private static INetworkServer.PlayerConnection createPlayerConnection(int id, String name) {
        INetworkServer.PlayerConnection connection = new NetworkKryoServer.KryoPlayerConnection(new TestConnection(id));
        connection.setPlayerName(name);
        return connection;
    }

    /** Creates a standard two-player room with HOST_ID as host, in the given phase. */
    private GameRoom createTwoPlayerRoom(GameState.Phase phase) {
        GameRoom room = new GameRoom("room-1", HOST_ID);

        INetworkServer.PlayerConnection host = createPlayerConnection(HOST_ID, HOST_NAME);
        INetworkServer.PlayerConnection guest = createPlayerConnection(GUEST_ID, GUEST_NAME);

        room.addPlayer(host, new Player(HOST_ID, HOST_NAME));
        room.addPlayer(guest, new Player(GUEST_ID, GUEST_NAME));
        transitionTo(room, phase);

        playerRooms.put(HOST_ID, room);
        playerRooms.put(GUEST_ID, room);

        return room;
    }

    /** Walks the state machine through valid transitions to reach the target phase. */
    private static void transitionTo(GameRoom room, GameState.Phase target) {
        if (target == GameState.Phase.WAITING) return;
        room.setPhase(GameState.Phase.PLAYING);
        if (target == GameState.Phase.PLAYING) return;
        room.setPhase(GameState.Phase.PLAYING);
        if (target == GameState.Phase.PLAYING) return;
        room.setPhase(target);
    }

    // ==================================================================
    // Authorization Tests (Issue #29 core requirements)
    // ==================================================================

    @Nested
    @DisplayName("Authorization â€” host-only pause policy")
    @Tag("FR4.1")
    class AuthorizationTests {

        @Test
        @Tag("FR4.1")
        @DisplayName("Host can pause a PLAYING game â€” phase becomes PAUSED and PauseEvent is broadcast")
        void hostCanPausePlayingGame() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.PAUSED, room.getPhase(),
                    "Room phase should transition to PAUSED when host pauses");

            List<PauseEvent> toHost = connector.getPacketsSentToOfType(HOST_ID, PauseEvent.class);
            List<PauseEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, PauseEvent.class);

            assertEquals(1, toHost.size(), "Host should receive PauseEvent");
            assertEquals(1, toGuest.size(), "Guest should receive PauseEvent");
        }

            @Test
            @Tag("A1")
            @Tag("FR4.1")
            @DisplayName("After host disconnects, promoted player can pause")
            void promotedPlayerCanPauseAfterHostDisconnect() {
                GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

                room.removePlayer(HOST_ID);
                playerRooms.remove(HOST_ID);

                INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
                handler.handle(guestConn, new PauseRequest());

                assertEquals(GameState.Phase.PAUSED, room.getPhase(),
                    "Promoted host should be able to pause after original host disconnects");

                List<PauseEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, PauseEvent.class);
                assertEquals(1, toGuest.size(),
                    "Promoted host should receive PauseEvent when pause succeeds");
            }

        @Test
        @Tag("FR4.1")
        @DisplayName("Non-host pause request is rejected â€” no phase change, no broadcast")
        void nonHostCannotPause() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
            handler.handle(guestConn, new PauseRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Room phase must remain PLAYING when non-host tries to pause");

            List<PauseEvent> allPauseEvents = connector.getAllPacketsOfType(PauseEvent.class);
            assertTrue(allPauseEvents.isEmpty(),
                    "No PauseEvent should be broadcast when non-host tries to pause");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("Non-host receives an ErrorPacket when pause is rejected")
        void nonHostReceivesErrorOnPauseAttempt() {
            createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
            handler.handle(guestConn, new PauseRequest());

            List<ErrorPacket> errors = connector.getPacketsSentToOfType(GUEST_ID, ErrorPacket.class);
            assertEquals(1, errors.size(),
                    "Non-host should receive exactly one ErrorPacket");
            assertNotNull(errors.get(0).message,
                    "ErrorPacket should contain a non-null message");

            // Host should NOT receive error for guest's rejected request
            List<ErrorPacket> hostErrors = connector.getPacketsSentToOfType(HOST_ID, ErrorPacket.class);
            assertTrue(hostErrors.isEmpty(),
                    "Host should NOT receive ErrorPacket for guest's rejected pause request");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("Host does NOT receive ErrorPacket on successful pause")
        void hostDoesNotReceiveErrorOnSuccessfulPause() {
            createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            List<ErrorPacket> errors = connector.getPacketsSentToOfType(HOST_ID, ErrorPacket.class);
            assertTrue(errors.isEmpty(),
                    "Host should not receive an ErrorPacket on a valid pause");
        }
    }

    // ==================================================================
    // State Validation Tests (Room phase preconditions)
    // ==================================================================

    @Nested
    @DisplayName("State validation â€” room phase preconditions")
    @Tag("TC6")
    class StateValidationTests {

        @Test
        @Tag("FR4.1")
        @DisplayName("Pause request rejected when room phase is WAITING")
        void pauseRejectedWhenWaiting() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.WAITING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.WAITING, room.getPhase(),
                    "Phase must remain WAITING");
            assertTrue(connector.getAllPacketsOfType(PauseEvent.class).isEmpty(),
                    "No PauseEvent should be broadcast");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("Pause request rejected when room is already PAUSED")
        void pauseRejectedWhenAlreadyPaused() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.PAUSED, room.getPhase(),
                    "Phase must remain PAUSED");
            assertTrue(connector.getAllPacketsOfType(PauseEvent.class).isEmpty(),
                    "No PauseEvent should be broadcast when already paused");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("Pause request rejected when room phase is FINISHED")
        void pauseRejectedWhenFinished() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.FINISHED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.FINISHED, room.getPhase(),
                    "Phase must remain FINISHED");
            assertTrue(connector.getAllPacketsOfType(PauseEvent.class).isEmpty(),
                    "No PauseEvent should be broadcast when game is finished");
        }

        @Test
        @DisplayName("Pause request from player not in any room does not throw")
        void pauseFromOrphanPlayerDoesNotThrow() {
            createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection orphan = createPlayerConnection(999, "Orphan");
            // Orphan is NOT in playerRooms

            assertDoesNotThrow(() -> handler.handle(orphan, new PauseRequest()));
            assertTrue(connector.getAllPacketsOfType(PauseEvent.class).isEmpty(),
                    "No PauseEvent should be broadcast for unknown player");
        }
    }

    // ==================================================================
    // PauseEvent content validation
    // ==================================================================

    @Nested
    @DisplayName("PauseEvent content")
    @Tag("FR4.1")
    class PauseEventContentTests {

        @Test
        @Tag("FR4.1")
        @DisplayName("PauseEvent requesterId matches the host who paused")
        void pauseEventContainsHostRequesterId() {
            createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            List<PauseEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, PauseEvent.class);
            assertEquals(1, toGuest.size());
            assertEquals(HOST_ID, toGuest.get(0).requesterId,
                    "PauseEvent.requesterId should be the host's connection ID");
        }

        @Test
        @Tag("FR4.1")
        @DisplayName("Both room members receive identical PauseEvent")
        void bothPlayersReceiveSamePauseEvent() {
            createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            List<PauseEvent> toHost = connector.getPacketsSentToOfType(HOST_ID, PauseEvent.class);
            List<PauseEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, PauseEvent.class);

            assertEquals(1, toHost.size());
            assertEquals(1, toGuest.size());
            assertEquals(toHost.get(0).requesterId, toGuest.get(0).requesterId,
                    "All players should receive PauseEvent with the same requesterId");
        }
    }

    // ==================================================================
    // Edge cases
    // ==================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Multiple pause requests from host while already PAUSED are idempotent")
        void duplicateHostPauseIsIdempotent() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.PAUSED, room.getPhase());
            connector.clear();

            // Second pause request â€” room is already PAUSED, should be rejected
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.PAUSED, room.getPhase(),
                    "Phase should remain PAUSED on duplicate pause");
            assertTrue(connector.getAllPacketsOfType(PauseEvent.class).isEmpty(),
                    "No additional PauseEvent should be broadcast");
        }

        @Test
        @DisplayName("Non-host pause attempt does not affect existing room state for other players")
        void nonHostPauseDoesNotCorruptRoomState() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
            handler.handle(guestConn, new PauseRequest());

            // Verify the room is completely untouched
            assertEquals(GameState.Phase.PLAYING, room.getPhase());
            assertTrue(room.isFull());
            assertEquals(2, room.getPlayerCount());

            // Now the host should still be able to pause successfully
            connector.clear();
            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new PauseRequest());

            assertEquals(GameState.Phase.PAUSED, room.getPhase(),
                    "Host should still be able to pause after a rejected non-host attempt");
        }
    }
}
