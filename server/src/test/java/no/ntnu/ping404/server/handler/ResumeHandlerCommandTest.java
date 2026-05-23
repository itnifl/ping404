package no.ntnu.ping404.server.handler;

import com.esotericsoftware.kryonet.Connection;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.ResumeEvent;
import no.ntnu.ping404.network.packets.ResumeRequest;
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
 * Tests for ResumeHandlerCommand â€” issue #29: Authorize pause/resume calls.
 *
 * Policy under test: Only the host (first player to join the room) may resume.
 * Non-host resume requests must be rejected with an ErrorPacket and must NOT
 * broadcast a ResumeEvent or change the room phase.
 */
class ResumeHandlerCommandTest {

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
    private ResumeHandlerCommand handler;

    @BeforeEach
    void setUp() {
        connector = new RecordingServerConnector();
        playerRooms = new ConcurrentHashMap<>();
        handler = new ResumeHandlerCommand(connector, playerRooms, null);
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
    @DisplayName("Authorization â€” host-only resume policy")
    @Tag("FR4.2")
    class AuthorizationTests {

        @Test
        @Tag("FR4.2")
        @DisplayName("Host can resume a PAUSED game â€” phase becomes PLAYING and ResumeEvent is broadcast")
        void hostCanResumePausedGame() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Room phase should transition to PLAYING when host resumes");

            List<ResumeEvent> toHost = connector.getPacketsSentToOfType(HOST_ID, ResumeEvent.class);
            List<ResumeEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, ResumeEvent.class);

            assertEquals(1, toHost.size(), "Host should receive ResumeEvent");
            assertEquals(1, toGuest.size(), "Guest should receive ResumeEvent");
        }

            @Test
            @Tag("A1")
            @Tag("FR4.2")
            @DisplayName("After host disconnects, promoted player can resume")
            void promotedPlayerCanResumeAfterHostDisconnect() {
                GameRoom room = createTwoPlayerRoom(GameState.Phase.PAUSED);

                room.removePlayer(HOST_ID);
                playerRooms.remove(HOST_ID);

                INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
                handler.handle(guestConn, new ResumeRequest());

                assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Promoted host should be able to resume after original host disconnects");

                List<ResumeEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, ResumeEvent.class);
                assertEquals(1, toGuest.size(),
                    "Promoted host should receive ResumeEvent when resume succeeds");
            }

        @Test
        @Tag("FR4.2")
        @DisplayName("Non-host resume request is rejected â€” no phase change, no broadcast")
        void nonHostCannotResume() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
            handler.handle(guestConn, new ResumeRequest());

            assertEquals(GameState.Phase.PAUSED, room.getPhase(),
                    "Room phase must remain PAUSED when non-host tries to resume");

            List<ResumeEvent> allResumeEvents = connector.getAllPacketsOfType(ResumeEvent.class);
            assertTrue(allResumeEvents.isEmpty(),
                    "No ResumeEvent should be broadcast when non-host tries to resume");
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Non-host receives an ErrorPacket when resume is rejected")
        void nonHostReceivesErrorOnResumeAttempt() {
            createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
            handler.handle(guestConn, new ResumeRequest());

            List<ErrorPacket> errors = connector.getPacketsSentToOfType(GUEST_ID, ErrorPacket.class);
            assertEquals(1, errors.size(),
                    "Non-host should receive exactly one ErrorPacket");
            assertNotNull(errors.get(0).message,
                    "ErrorPacket should contain a non-null message");

            // Host should NOT receive error for guest's rejected request
            List<ErrorPacket> hostErrors = connector.getPacketsSentToOfType(HOST_ID, ErrorPacket.class);
            assertTrue(hostErrors.isEmpty(),
                    "Host should NOT receive ErrorPacket for guest's rejected resume request");
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Host does NOT receive ErrorPacket on successful resume")
        void hostDoesNotReceiveErrorOnSuccessfulResume() {
            createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            List<ErrorPacket> errors = connector.getPacketsSentToOfType(HOST_ID, ErrorPacket.class);
            assertTrue(errors.isEmpty(),
                    "Host should not receive an ErrorPacket on a valid resume");
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
        @Tag("FR4.2")
        @DisplayName("Resume request rejected when room phase is WAITING")
        void resumeRejectedWhenWaiting() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.WAITING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.WAITING, room.getPhase(),
                    "Phase must remain WAITING");
            assertTrue(connector.getAllPacketsOfType(ResumeEvent.class).isEmpty(),
                    "No ResumeEvent should be broadcast");
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Resume request rejected when room phase is PLAYING")
        void resumeRejectedWhenAlreadyPlaying() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Phase must remain PLAYING");
            assertTrue(connector.getAllPacketsOfType(ResumeEvent.class).isEmpty(),
                    "No ResumeEvent should be broadcast when not paused");
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Resume request rejected when room phase is FINISHED")
        void resumeRejectedWhenFinished() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.FINISHED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.FINISHED, room.getPhase(),
                    "Phase must remain FINISHED");
            assertTrue(connector.getAllPacketsOfType(ResumeEvent.class).isEmpty(),
                    "No ResumeEvent should be broadcast when game is finished");
        }

        @Test
        @DisplayName("Resume request from player not in any room does not throw")
        void resumeFromOrphanPlayerDoesNotThrow() {
            createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection orphan = createPlayerConnection(999, "Orphan");
            // Orphan is NOT in playerRooms

            assertDoesNotThrow(() -> handler.handle(orphan, new ResumeRequest()));
            assertTrue(connector.getAllPacketsOfType(ResumeEvent.class).isEmpty(),
                    "No ResumeEvent should be broadcast for unknown player");
        }
    }

    // ==================================================================
    // ResumeEvent content validation
    // ==================================================================

    @Nested
    @DisplayName("ResumeEvent content")
    @Tag("FR4.2")
    class ResumeEventContentTests {

        @Test
        @Tag("FR4.2")
        @DisplayName("ResumeEvent requesterId matches the host who resumed")
        void resumeEventContainsHostRequesterId() {
            createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            List<ResumeEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, ResumeEvent.class);
            assertEquals(1, toGuest.size());
            assertEquals(HOST_ID, toGuest.get(0).requesterId,
                    "ResumeEvent.requesterId should be the host's connection ID");
        }

        @Test
        @Tag("FR4.2")
        @DisplayName("Both room members receive identical ResumeEvent")
        void bothPlayersReceiveSameResumeEvent() {
            createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            List<ResumeEvent> toHost = connector.getPacketsSentToOfType(HOST_ID, ResumeEvent.class);
            List<ResumeEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, ResumeEvent.class);

            assertEquals(1, toHost.size());
            assertEquals(1, toGuest.size());
            assertEquals(toHost.get(0).requesterId, toGuest.get(0).requesterId,
                    "All players should receive ResumeEvent with the same requesterId");
        }
    }

    // ==================================================================
    // Edge cases
    // ==================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Multiple resume requests from host while already PLAYING are idempotent")
        void duplicateHostResumeIsIdempotent() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase());
            connector.clear();

            // Second resume request â€” room is already PLAYING, should be rejected
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Phase should remain PLAYING on duplicate resume");
            assertTrue(connector.getAllPacketsOfType(ResumeEvent.class).isEmpty(),
                    "No additional ResumeEvent should be broadcast");
        }

        @Test
        @DisplayName("Non-host resume attempt does not affect existing room state for other players")
        void nonHostResumeDoesNotCorruptRoomState() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PAUSED);

            INetworkServer.PlayerConnection guestConn = createPlayerConnection(GUEST_ID, GUEST_NAME);
            handler.handle(guestConn, new ResumeRequest());

            // Verify the room is completely untouched
            assertEquals(GameState.Phase.PAUSED, room.getPhase());
            assertTrue(room.isFull());
            assertEquals(2, room.getPlayerCount());

            // Now the host should still be able to resume successfully
            connector.clear();
            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Host should still be able to resume after a rejected non-host attempt");
        }

        @Test
        @DisplayName("Resume after pause round-trip â€” host pauses then resumes")
        void pauseResumeRoundTrip() {
            GameRoom room = createTwoPlayerRoom(GameState.Phase.PLAYING);

            PauseHandlerCommand pauseHandler = new PauseHandlerCommand(connector, playerRooms, null);

            INetworkServer.PlayerConnection hostConn = createPlayerConnection(HOST_ID, HOST_NAME);

            // Host pauses
            pauseHandler.handle(hostConn, new no.ntnu.ping404.network.packets.PauseRequest());
            // Current code allows anyone to pause, so phase will be PAUSED regardless
            // After authorization is implemented, only host should be able to do this
            connector.clear();

            // Host resumes
            handler.handle(hostConn, new ResumeRequest());

            assertEquals(GameState.Phase.PLAYING, room.getPhase(),
                    "Phase should return to PLAYING after pauseâ†’resume round-trip");

            List<ResumeEvent> toGuest = connector.getPacketsSentToOfType(GUEST_ID, ResumeEvent.class);
            assertEquals(1, toGuest.size(),
                    "Guest should receive ResumeEvent after round-trip");
        }
    }
}
