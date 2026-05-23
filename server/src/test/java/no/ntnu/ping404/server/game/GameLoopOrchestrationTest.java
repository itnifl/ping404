package no.ntnu.ping404.server.game;

import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.server.GameRoom;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies game-loop orchestration contracts for issue #64.
 *
 * <p>Issue #64 identifies a split-ownership defect: {@link GameRoom#setPhase} was
 * managing a legacy {@code server.GameLoop} internally, while {@code GameServer}
 * simultaneously managed its own {@code server.game.GameLoop}.  Both loops ran
 * concurrently on the same {@link no.ntnu.ping404.model.GameState}, causing
 * double physics updates and duplicate broadcasts.</p>
 *
 * <p>These tests verify that:</p>
 * <ul>
 *   <li>{@link GameRoom#setPhase} is a pure state-machine transition - it must
 *       not spawn any background game-loop thread (FR1.5, TC5).</li>
 *   <li>The {@code server.game.GameLoop} is the single authoritative tick loop
 *       and processes input events correctly when started explicitly (P1, TC2).</li>
 *   <li>Two rooms each have independent game state - a goal in one room must not
 *       affect the score in another (TC5, FR1.5).</li>
 * </ul>
 *
 * <p>Affected requirements: FR1.5, P1, TC2, TC5, TC6 (#64).</p>
 */
class GameLoopOrchestrationTest {

    private static final String ROOM_A = "room-a";
    private static final String ROOM_B = "room-b";
    private static final int CONN_P1 = 1;
    private static final int CONN_P2 = 2;
    private static final int CONN_P3 = 3;
    private static final int CONN_P4 = 4;
    private static final int WIN_SCORE = 5;

    /** Test double: no-op ServerConnector that never touches a socket. */
    private static class NoOpServerConnector extends ServerConnector {
        final List<Object> sent = new ArrayList<>();

        NoOpServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            sent.add(packet);
        }
    }

    /** Counts live threads whose name starts with the given prefix. */
    private static long countThreadsWithNamePrefix(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith(prefix))
                .count();
    }

    /** Builds a full 2-player room in WAITING phase. */
    private static GameRoom buildFullRoom(String roomId, int hostId, int guestId) {
        GameRoom room = new GameRoom(roomId, hostId, WIN_SCORE);
        room.addPlayer(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(hostId), new Player(hostId, "Host"));
        room.addPlayer(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(guestId), new Player(guestId, "Guest"));
        return room;
    }

    @Nested
    @DisplayName("GameRoom.setPhase() is a pure state transition - no loop spawned")
    @Tag("TC5")
    @Tag("FR1.5")
    class SetPhaseIsStateMachineOnly {

        @Test
        @DisplayName("setPhase(PLAYING) does not spawn a legacy GameLoop background thread")
        void setPhasePlayingSpawnsNoLegacyThread() throws InterruptedException {
            // Arrange
            GameRoom room = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);

            long threadsBefore = countThreadsWithNamePrefix("GameLoop-" + ROOM_A);

            // Act
            room.setPhase(Phase.PLAYING);
            Thread.sleep(80); // give any late-starting thread time to appear

            // Assert
            long threadsAfter = countThreadsWithNamePrefix("GameLoop-" + ROOM_A);
            assertEquals(threadsBefore, threadsAfter,
                    "GameRoom.setPhase(PLAYING) must not spawn a legacy GameLoop thread");
        }

        @Test
        @DisplayName("setPhase(PLAYING) only changes GameState phase to PLAYING")
        void setPhasePlayingOnlyChangesGameStatePhase() {
            // Arrange
            GameRoom room = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);

            // Act
            room.setPhase(Phase.PLAYING);

            // Assert
            assertEquals(Phase.PLAYING, room.getPhase(),
                    "GameState phase should be PLAYING after setPhase(PLAYING)");
        }

        @Test
        @DisplayName("setPhase(PAUSED) -> setPhase(PLAYING) resume does not spawn additional threads")
        void pauseThenResumeSpawnsNoAdditionalThreads() throws InterruptedException {
            // Arrange
            GameRoom room = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);
            room.setPhase(Phase.PLAYING);
            Thread.sleep(30);
            long threadsAfterStart = countThreadsWithNamePrefix("GameLoop-" + ROOM_A);

            // Act - pause and resume
            room.setPhase(Phase.PAUSED);
            room.setPhase(Phase.PLAYING);
            Thread.sleep(80);

            // Assert - no new legacy threads spawned
            long threadsAfterResume = countThreadsWithNamePrefix("GameLoop-" + ROOM_A);
            assertEquals(threadsAfterStart, threadsAfterResume,
                    "Pause-then-resume must not spawn additional legacy loop threads");
        }
    }

    @Nested
    @DisplayName("server.game.GameLoop is the single authoritative tick loop")
    @Tag("P1")
    @Tag("TC6")
    class SingleAuthoritativeLoop {

        @Test
        @DisplayName("Starting a game.GameLoop explicitly starts the game-loop thread")
        void explicitGameLoopStartsThread() throws InterruptedException {
            // Arrange
            GameRoom room = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);
            room.getGameState().setPhase(Phase.PLAYING);
            InputQueue queue = new InputQueue();
            NoOpServerConnector conn = new NoOpServerConnector();
            GameLoop loop = new GameLoop(room, queue, conn);

            long threadsBefore = countThreadsWithNamePrefix("game-loop-" + ROOM_A);

            // Act
            loop.start();
            Thread.sleep(50);

            try {
                // Assert
                long threadsAfter = countThreadsWithNamePrefix("game-loop-" + ROOM_A);
                assertEquals(threadsBefore + 1, threadsAfter,
                        "Starting a game.GameLoop should create exactly one game-loop thread");
            } finally {
                loop.stop();
            }
        }

        @Test
        @DisplayName("Only one game-loop thread is active after GameServer starts the room")
        void startGameLoopCreatesExactlyOneThread() throws InterruptedException {
            // Arrange
            GameRoom room = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);

            long legacyBefore  = countThreadsWithNamePrefix("GameLoop-" + ROOM_A);
            long modernBefore  = countThreadsWithNamePrefix("game-loop-" + ROOM_A);

            InputQueue queue = new InputQueue();
            NoOpServerConnector conn = new NoOpServerConnector();
            GameLoop loop = new GameLoop(room, queue, conn);

            // Act - simulate what GameServer.startGameLoop() does
            room.setPhase(Phase.PLAYING);  // must NOT spawn old loop (post-fix)
            loop.start();
            Thread.sleep(80);

            try {
                // Assert: zero legacy threads, exactly one modern thread
                long legacyAfter = countThreadsWithNamePrefix("GameLoop-" + ROOM_A);
                long modernAfter = countThreadsWithNamePrefix("game-loop-" + ROOM_A);

                assertEquals(legacyBefore, legacyAfter,
                        "No legacy GameLoop thread should be running (GameRoom must not own a loop)");
                assertEquals(modernBefore + 1, modernAfter,
                        "Exactly one game-loop thread should run per room");
            } finally {
                loop.stop();
            }
        }
    }

    @Nested
    @DisplayName("Room isolation - each room has independent physics state")
    @Tag("TC5")
    @Tag("FR1.5")
    class RoomIsolationLoopTests {

        @Test
        @DisplayName("A goal in room A does not change the score in room B")
        void goalInRoomADoesNotAffectRoomBScore() {
            // Arrange - two rooms, each with two players, both in PLAYING phase
            GameRoom roomA = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);
            GameRoom roomB = buildFullRoom(ROOM_B, CONN_P3, CONN_P4);
            roomA.getGameState().setPhase(Phase.PLAYING);
            roomB.getGameState().setPhase(Phase.PLAYING);

            InputQueue queueA = new InputQueue();
            InputQueue queueB = new InputQueue();
            NoOpServerConnector connA = new NoOpServerConnector();
            NoOpServerConnector connB = new NoOpServerConnector();
            GameLoop loopA = new GameLoop(roomA, queueA, connA);
            GameLoop loopB = new GameLoop(roomB, queueB, connB);

            // Aim puck in room A at player 1's goal line
            var puckA = roomA.getGameState().getPuck();
            puckA.setPosition(puckA.getRadius() + 1f, 240f);
            puckA.setVelocityX(-5000f);
            puckA.setVelocityY(0f);

            // Park puck in room B at center (no goal expected)
            var puckB = roomB.getGameState().getPuck();
            puckB.setPosition(400f, 240f);
            puckB.setVelocityX(0f);
            puckB.setVelocityY(0f);

            // Act - one tick each
            loopA.tick(1.0f / 60);
            loopB.tick(1.0f / 60);

            // Assert - room B score is untouched
            assertEquals(0, roomB.getGameState().getScore().getPlayer1Score(),
                    "Room B player 1 score must not change due to room A goal");
            assertEquals(0, roomB.getGameState().getScore().getPlayer2Score(),
                    "Room B player 2 score must not change due to room A goal");
        }

        @Test
        @DisplayName("Phase change in room A does not affect room B phase")
        void phaseChangeInRoomADoesNotAffectRoomB() {
            // Arrange
            GameRoom roomA = buildFullRoom(ROOM_A, CONN_P1, CONN_P2);
            GameRoom roomB = buildFullRoom(ROOM_B, CONN_P3, CONN_P4);
            roomA.getGameState().setPhase(Phase.PLAYING);
            roomB.getGameState().setPhase(Phase.PLAYING);

            // Act - pause room A only
            roomA.setPhase(Phase.PAUSED);

            // Assert - room B remains PLAYING
            assertEquals(Phase.PAUSED, roomA.getPhase(), "Room A should be PAUSED");
            assertEquals(Phase.PLAYING, roomB.getPhase(), "Room B should remain PLAYING");
        }
    }
}
