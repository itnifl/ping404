package no.ntnu.ping404.server.game;


import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.model.Score;
import no.ntnu.ping404.network.GameConfig;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.GameStateSnapshot;
import no.ntnu.ping404.network.packets.GoalScored;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.server.metrics.MetricEvent;
import no.ntnu.ping404.server.metrics.MetricsCollector;
import no.ntnu.ping404.server.metrics.NoOpGeoLocationService;
import no.ntnu.ping404.utils.Constants;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GameLoop} - the 60 Hz server-authoritative tick loop (issue #14).
 *
 * <p>Tests call the package-private {@code tick()} method directly so they are
 * deterministic and do not require real threads or wall-clock timing.</p>
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>Input queue consumption (Consumer side of Producer-Consumer)</li>
 *   <li>Physics gated by {@link Phase#PLAYING}</li>
 *   <li>Goal detection --> score update --> {@link GoalScored} broadcast</li>
 *   <li>Win condition --> {@link GameOver} broadcast + phase transition to FINISHED</li>
 *   <li>{@link GameStateSnapshot} broadcast every tick</li>
 *   <li>Pause / resume via InputQueue</li>
 *   <li>Goal reset delay (puck stationary between goals)</li>
 * </ul>
 */
class GameLoopTest {

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------

    private static final String ROOM_ID = "test-room";
    private static final int CONN_P1 = 10;
    private static final int CONN_P2 = 20;
    private static final String NAME_P1 = "Alice";
    private static final String NAME_P2 = "Bob";
    private static final int WIN_SCORE = 5;
    private static final float TICK_DELTA = 1.0f / 60;

    // ------------------------------------------------------------------
    // Test Double: RecordingServerConnector
    // ------------------------------------------------------------------

    /**
     * Captures all packets sent via {@code connector.send()} so tests can
     * inspect what the GameLoop broadcast without a real network.
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

        /** Returns all packets of the given type sent to a specific connection. */
        <T> List<T> getPacketsSentToOfType(int connectionId, Class<T> type) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        /** Returns all packets of the given type sent to any connection. */
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
    // Fixtures
    // ------------------------------------------------------------------

    private RecordingServerConnector connector;
    private GameRoom room;
    private InputQueue inputQueue;
    private GameLoop loop;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        GameConfig.reset();
        connector = new RecordingServerConnector();
        room = new GameRoom(ROOM_ID, CONN_P1, WIN_SCORE);

        // Add two players so the room is full
        PlayerConnection pc1 = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(CONN_P1);
        PlayerConnection pc2 = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(CONN_P2);
        room.addPlayer(pc1, new Player(CONN_P1, NAME_P1));
        room.addPlayer(pc2, new Player(CONN_P2, NAME_P2));

        // Transition to PLAYING (the normal state for the game loop)
        room.setPhase(Phase.PLAYING);

        inputQueue = new InputQueue();
        metricsCollector = new MetricsCollector(new NoOpGeoLocationService());
        loop = new GameLoop(room, inputQueue, connector, metricsCollector);
    }

    @AfterEach
    void tearDown() {
        loop.stop();
        GameConfig.reset();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Places the puck at center with zero velocity so physics produces no goals. */
    private void parkPuckAtCenter() {
        var puck = room.getGameState().getPuck();
        puck.setPosition(Constants.boardCenterX(), Constants.boardCenterY());
        puck.setVelocityX(0);
        puck.setVelocityY(0);
    }

    /** Aims the puck so it will cross the left goal line (Player 1's side --> Player 2 scores). */
    private void aimPuckAtPlayer1Goal() {
        var puck = room.getGameState().getPuck();
        puck.setPosition(puck.getRadius() + 1f, Constants.boardCenterY());
        puck.setVelocityX(-5000f); // fast enough to cross in one tick
        puck.setVelocityY(0);
    }

    /** Aims the puck so it will cross the right goal line (Player 2's side --> Player 1 scores). */
    private void aimPuckAtPlayer2Goal() {
        var puck = room.getGameState().getPuck();
        puck.setPosition(Constants.DEFAULT_FIELD_WIDTH - puck.getRadius() - 1f, Constants.boardCenterY());
        puck.setVelocityX(5000f);
        puck.setVelocityY(0);
    }

    private void recreateLoopWithRuntimeTargets(int simulationHz, int broadcastHz, double jitterMs) {
        loop.stop();
        GameConfig.setRuntimeTargets(simulationHz, broadcastHz, jitterMs);
        loop = new GameLoop(room, inputQueue, connector, metricsCollector);
    }

    // ==================================================================
    // 1. Input Queue Consumption (Consumer side)
    // ==================================================================

    @Nested
    @DisplayName("Input queue consumption - Consumer side of Producer-Consumer")
    @Tag("FR2")
    class InputQueueTests {

        @Test
        @DisplayName("Paddle move event updates the authoritative player position")
        void paddleMoveUpdatesPlayerPosition() {
            parkPuckAtCenter();

            float newX = 50f;
            float newY = 200f;
            inputQueue.enqueue(InputEvent.paddleMove(CONN_P1, newX, newY));

            loop.tick(TICK_DELTA);

            Player p1 = room.getGameState().getPlayer(CONN_P1);
            assertEquals(newX, p1.getX(), 0.001f, "Player X should be updated from InputEvent");
            assertEquals(newY, p1.getY(), 0.001f, "Player Y should be updated from InputEvent");
        }

        @Test
        @DisplayName("Multiple paddle moves in one tick are all applied in order")
        void multiplePaddleMovesAppliedInOrder() {
            parkPuckAtCenter();

            inputQueue.enqueue(InputEvent.paddleMove(CONN_P1, 10f, 10f));
            inputQueue.enqueue(InputEvent.paddleMove(CONN_P1, 30f, 60f));
            inputQueue.enqueue(InputEvent.paddleMove(CONN_P1, 50f, 100f));

            loop.tick(TICK_DELTA);

            Player p1 = room.getGameState().getPlayer(CONN_P1);
            assertEquals(50f, p1.getX(), 0.001f, "Last paddle move X should win");
            assertEquals(100f, p1.getY(), 0.001f, "Last paddle move Y should win");
        }

        @Test
        @DisplayName("Paddle move for unknown connection ID does not throw")
        void paddleMoveForUnknownConnectionDoesNotThrow() {
            parkPuckAtCenter();
            inputQueue.enqueue(InputEvent.paddleMove(999, 100f, 100f));
            assertDoesNotThrow(() -> loop.tick(TICK_DELTA));
        }

        @Test
        @DisplayName("Input queue is drained each tick - events are not re-processed")
        void inputQueueIsDrainedEachTick() {
            parkPuckAtCenter();
            inputQueue.enqueue(InputEvent.paddleMove(CONN_P1, 77f, 88f));

            loop.tick(TICK_DELTA);
            assertTrue(inputQueue.isEmpty(), "Queue should be empty after tick drains it");

            // Change position directly, then tick again - old event must not reappear
            Player p1 = room.getGameState().getPlayer(CONN_P1);
            p1.setX(0f);
            p1.setY(0f);

            loop.tick(TICK_DELTA);
            assertEquals(0f, p1.getX(), 0.001f, "Old input event should not be re-applied");
        }
    }

    // ==================================================================
    // 2. Pause / Resume via InputQueue
    // ==================================================================

    @Nested
    @DisplayName("Pause and resume via input queue")
    @Tag("FR4.1")
    class PauseResumeTests {

        @Test
        @DisplayName("Pause request transitions phase from PLAYING to PAUSED")
        void pauseRequestTransitionsToPlaying() {
            parkPuckAtCenter();
            inputQueue.enqueue(InputEvent.pauseRequest(CONN_P1));

            loop.tick(TICK_DELTA);

            assertEquals(Phase.PAUSED, room.getPhase(), "Phase should be PAUSED after pause request");
        }

        @Test
        @DisplayName("Resume request transitions phase from PAUSED to PLAYING")
        void resumeRequestTransitionsToPlaying() {
            parkPuckAtCenter();
            room.setPhase(Phase.PAUSED);
            inputQueue.enqueue(InputEvent.resumeRequest(CONN_P1));

            loop.tick(TICK_DELTA);

            assertEquals(Phase.PLAYING, room.getPhase(), "Phase should be PLAYING after resume request");
        }

        @Test
        @DisplayName("Pause request is ignored if phase is not PLAYING")
        void pauseIgnoredWhenNotPlaying() {
            room.setPhase(Phase.WAITING);
            inputQueue.enqueue(InputEvent.pauseRequest(CONN_P1));

            loop.tick(TICK_DELTA);

            assertEquals(Phase.WAITING, room.getPhase(), "Phase should stay WAITING");
        }

        @Test
        @DisplayName("Resume request is ignored if phase is not PAUSED")
        void resumeIgnoredWhenNotPaused() {
            room.setPhase(Phase.PLAYING);
            inputQueue.enqueue(InputEvent.resumeRequest(CONN_P1));

            loop.tick(TICK_DELTA);

            assertEquals(Phase.PLAYING, room.getPhase(), "Phase should stay PLAYING (was not PAUSED)");
        }
    }

    // ==================================================================
    // 3. Phase guards - physics only runs while PLAYING
    // ==================================================================

    @Nested
    @DisplayName("Phase guards - physics gated by PLAYING")
    @Tag("FR2")
    class PhaseGuardTests {

        @Test
        @DisplayName("Tick does not broadcast GameStateSnapshot when phase is WAITING")
        void noSnapshotWhenWaiting() {
            room.setPhase(Phase.WAITING);
            parkPuckAtCenter();

            loop.tick(TICK_DELTA);

            assertTrue(connector.getAllPacketsOfType(GameStateSnapshot.class).isEmpty(),
                    "No snapshot should be broadcast while WAITING");
        }

        @Test
        @DisplayName("Tick does not broadcast GameStateSnapshot when phase is PAUSED")
        void noSnapshotWhenPaused() {
            room.setPhase(Phase.PAUSED);
            parkPuckAtCenter();

            loop.tick(TICK_DELTA);

            assertTrue(connector.getAllPacketsOfType(GameStateSnapshot.class).isEmpty(),
                    "No snapshot should be broadcast while PAUSED");
        }

        @Test
        @DisplayName("Tick does not move puck when phase is FINISHED")
        void puckDoesNotMoveWhenFinished() {
            room.setPhase(Phase.FINISHED);
            var puck = room.getGameState().getPuck();
            puck.setPosition(100f, 100f);
            puck.setVelocityX(300f);
            puck.setVelocityY(0);
            float originalX = puck.getX();

            loop.tick(TICK_DELTA);

            assertEquals(originalX, puck.getX(), 0.001f,
                    "Puck should not move when phase is FINISHED");
        }

        @Test
        @DisplayName("Input queue is still drained even when phase is not PLAYING")
        void inputQueueDrainedRegardlessOfPhase() {
            room.setPhase(Phase.PAUSED);
            inputQueue.enqueue(InputEvent.resumeRequest(CONN_P1));

            loop.tick(TICK_DELTA);

            assertTrue(inputQueue.isEmpty(), "Queue should be drained even when not PLAYING");
            assertEquals(Phase.PLAYING, room.getPhase(),
                    "Resume should be processed even though physics was paused");
        }
    }

    // ==================================================================
    // 4. State broadcast (GameStateSnapshot)
    // ==================================================================

    @Nested
    @DisplayName("GameStateSnapshot broadcast")
    @Tag("P2")
    class StateBroadcastTests {

        @Test
        @DisplayName("Each tick broadcasts a GameStateSnapshot to both players")
        void snapshotBroadcastToBothPlayers() {
            parkPuckAtCenter();

            loop.tick(TICK_DELTA);

            List<GameStateSnapshot> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameStateSnapshot.class);
            List<GameStateSnapshot> toP2 = connector.getPacketsSentToOfType(CONN_P2, GameStateSnapshot.class);
            assertEquals(1, toP1.size(), "Player 1 should receive one snapshot per tick");
            assertEquals(1, toP2.size(), "Player 2 should receive one snapshot per tick");
        }

        @Test
        @DisplayName("Snapshot contains current puck position and score")
        void snapshotContainsCorrectData() {
            parkPuckAtCenter();
            Score score = room.getGameState().getScore();
            score.setPlayer1Score(2);
            score.setPlayer2Score(3);

            loop.tick(TICK_DELTA);

            GameStateSnapshot snap = connector.getAllPacketsOfType(GameStateSnapshot.class).get(0);
            assertEquals(2, snap.player1Score, "Snapshot P1 score should match");
            assertEquals(3, snap.player2Score, "Snapshot P2 score should match");
            assertEquals(Phase.PLAYING, snap.phase, "Snapshot phase should be PLAYING");
            assertNotNull(snap.puckPosition, "Snapshot should have puck position");
            assertNotNull(snap.puckVelocity, "Snapshot should have puck velocity");
        }

        @Test
        @DisplayName("Broadcast frequency is capped below simulation frequency")
        void broadcastIsCappedByConfiguredMaxFrequency() {
            parkPuckAtCenter();
            recreateLoopWithRuntimeTargets(60, 20, 10.0);
            connector.clear();

            for (int i = 0; i < 6; i++) {
                loop.tick(TICK_DELTA);
            }

            List<GameStateSnapshot> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameStateSnapshot.class);
            List<GameStateSnapshot> toP2 = connector.getPacketsSentToOfType(CONN_P2, GameStateSnapshot.class);
            assertEquals(2, toP1.size(), "At 60Hz simulation with 20Hz broadcast, 6 ticks should produce 2 snapshots");
            assertEquals(2, toP2.size(), "Both players should receive the same capped number of snapshots");
        }

        @Test
        @DisplayName("Broadcast equals simulation when max frequency matches tick rate")
        void broadcastMatchesSimulationWhenConfiguredEqual() {
            parkPuckAtCenter();
            recreateLoopWithRuntimeTargets(60, 60, 10.0);
            connector.clear();

            for (int i = 0; i < 4; i++) {
                loop.tick(TICK_DELTA);
            }

            List<GameStateSnapshot> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameStateSnapshot.class);
            List<GameStateSnapshot> toP2 = connector.getPacketsSentToOfType(CONN_P2, GameStateSnapshot.class);
            assertEquals(4, toP1.size(), "At equal simulation and broadcast rates, each tick should broadcast");
            assertEquals(4, toP2.size(), "Both players should receive one snapshot per tick");
        }

        @Test
        @DisplayName("Intermediate snapshots are replaced so the newest state is broadcast")
        void intermediateSnapshotsUseNewestState() {
            parkPuckAtCenter();
            recreateLoopWithRuntimeTargets(60, 20, 10.0);

            loop.tick(TICK_DELTA);
            connector.clear();

            var puck = room.getGameState().getPuck();
            puck.setVelocityX(0f);
            puck.setVelocityY(0f);

            puck.setPosition(100f, Constants.boardCenterY());
            loop.tick(TICK_DELTA);

            puck.setPosition(200f, Constants.boardCenterY());
            loop.tick(TICK_DELTA);

            puck.setPosition(300f, Constants.boardCenterY());
            loop.tick(TICK_DELTA);

            List<GameStateSnapshot> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameStateSnapshot.class);
            assertEquals(1, toP1.size(), "Only the newest pending snapshot should be sent on the next broadcast slot");
            assertEquals(300f, toP1.get(0).puckPosition.x, 0.001f,
                "The sent snapshot should contain the latest puck position, not an outdated intermediate one");
        }

        @Test
        @DisplayName("Metrics queue overload reduces non-critical snapshot broadcast frequency")
        void overloadReducesSnapshotBroadcastFrequency() {
            parkPuckAtCenter();
            recreateLoopWithRuntimeTargets(60, 20, 10.0);
            connector.clear();

            int overloadEvents = metricsCollector.getThresholds().getMaxIncomingQueueDepth() + 1;
            for (int i = 0; i < overloadEvents; i++) {
                metricsCollector.record(new MetricEvent.PlayerJoinedEvent(ROOM_ID, 1_000 + i, null));
            }

            for (int i = 0; i < 6; i++) {
                loop.tick(TICK_DELTA);
            }

            List<GameStateSnapshot> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameStateSnapshot.class);
            List<GameStateSnapshot> toP2 = connector.getPacketsSentToOfType(CONN_P2, GameStateSnapshot.class);
            assertEquals(1, toP1.size(), "Under overload the loop should reduce non-critical snapshot frequency");
            assertEquals(1, toP2.size(), "Both players should observe the same degraded snapshot rate");
        }
    }

    // ==================================================================
    // 5. Goal detection and scoring
    // ==================================================================

    @Nested
    @DisplayName("Goal detection and scoring")
    @Tag("FR3")
    class GoalScoringTests {

        @Test
        @DisplayName("Puck crossing Player 1's goal line increments Player 2's score")
        void player1GoalIncrementsPlayer2Score() {
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            Score score = room.getGameState().getScore();
            assertEquals(0, score.getPlayer1Score(), "Player 1 score should stay 0");
            assertEquals(1, score.getPlayer2Score(), "Player 2 should have scored");
        }

        @Test
        @DisplayName("Puck crossing Player 2's goal line increments Player 1's score")
        void player2GoalIncrementsPlayer1Score() {
            aimPuckAtPlayer2Goal();

            loop.tick(TICK_DELTA);

            Score score = room.getGameState().getScore();
            assertEquals(1, score.getPlayer1Score(), "Player 1 should have scored");
            assertEquals(0, score.getPlayer2Score(), "Player 2 score should stay 0");
        }

        @Test
        @DisplayName("GoalScored packet is broadcast to both players on goal")
        void goalScoredBroadcast() {
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            List<GoalScored> toP1 = connector.getPacketsSentToOfType(CONN_P1, GoalScored.class);
            List<GoalScored> toP2 = connector.getPacketsSentToOfType(CONN_P2, GoalScored.class);
            assertEquals(1, toP1.size(), "Player 1 should receive GoalScored");
            assertEquals(1, toP2.size(), "Player 2 should receive GoalScored");
        }

        @Test
        @DisplayName("GoalScored packet contains updated score")
        void goalScoredContainsUpdatedScore() {
            // Give P1 an existing score first
            room.getGameState().getScore().setPlayer1Score(2);
            aimPuckAtPlayer2Goal();

            loop.tick(TICK_DELTA);

            GoalScored packet = connector.getAllPacketsOfType(GoalScored.class).get(0);
            assertEquals(3, packet.player1Score, "GoalScored should reflect incremented P1 score");
            assertEquals(0, packet.player2Score, "GoalScored should reflect unchanged P2 score");
        }

        @Test
        @DisplayName("After Player 1 concedes, puck is placed on Player 1's half during reset delay")
        @Tag("FR2.10")
        void puckResetAfterNonWinningGoal() {
            aimPuckAtPlayer1Goal(); // PLAYER_1_GOAL --> Player 1 concedes

            loop.tick(TICK_DELTA);

            var puck = room.getGameState().getPuck();
            assertEquals(Constants.player1HalfCenterX(), puck.getX(), 0.5f,
                    "Puck X should be on Player 1's half after Player 1 concedes (FR2.10)");
            assertEquals(Constants.boardCenterY(), puck.getY(), 0.5f,
                    "Puck Y should be at board centre height");
            assertEquals(0f, puck.getVelocityX(), 0.001f, "Puck vX should be 0 during reset delay");
            assertEquals(0f, puck.getVelocityY(), 0.001f, "Puck vY should be 0 during reset delay");
        }
    }

    @Nested
    @DisplayName("Puck repositioned to conceding player's half after goal (FR2.10)")
    @Tag("FR2.10")
    class PuckRepositioningTests {

        @Test
        @DisplayName("After Player 1 concedes (PLAYER_1_GOAL) puck X is on the left half")
        void afterPlayer1GoalPuckIsOnPlayer1Half() {
            aimPuckAtPlayer1Goal(); // Player 1 concedes

            loop.tick(TICK_DELTA);

            float puckX = room.getGameState().getPuck().getX();
            assertTrue(puckX < Constants.boardCenterX(),
                    "After Player 1 concedes puck must be left of centre (on Player 1's half)");
            assertEquals(Constants.player1HalfCenterX(), puckX, 0.5f,
                    "Puck X should equal player1HalfCenterX (board/4)");
        }

        @Test
        @DisplayName("After Player 2 concedes (PLAYER_2_GOAL) puck X is on the right half")
        void afterPlayer2GoalPuckIsOnPlayer2Half() {
            aimPuckAtPlayer2Goal(); // Player 2 concedes

            loop.tick(TICK_DELTA);

            float puckX = room.getGameState().getPuck().getX();
            assertTrue(puckX > Constants.boardCenterX(),
                    "After Player 2 concedes puck must be right of centre (on Player 2's half)");
            assertEquals(Constants.player2HalfCenterX(), puckX, 0.5f,
                    "Puck X should equal player2HalfCenterX (board*3/4)");
        }

        @Test
        @DisplayName("After goal puck velocity is zero during the reset delay")
        void puckIsStationaryDuringResetDelay() {
            aimPuckAtPlayer2Goal();

            loop.tick(TICK_DELTA);

            var puck = room.getGameState().getPuck();
            assertEquals(0f, puck.getVelocityX(), 0.001f, "Puck must be stationary during goal delay");
            assertEquals(0f, puck.getVelocityY(), 0.001f, "Puck must be stationary during goal delay");
        }

        @Test
        @DisplayName("Puck Y is always at board centre height after a goal")
        void puckYIsAtBoardCentreAfterGoal() {
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            assertEquals(Constants.boardCenterY(), room.getGameState().getPuck().getY(), 0.5f,
                    "Puck Y must always be at board centre height after a goal");
        }
    }

    // ==================================================================
    // 6. Win condition
    // ==================================================================

    @Nested
    @DisplayName("Win condition - GameOver broadcast and phase transition")
    @Tag("FR3.2")
    class WinConditionTests {

        @Test
        @DisplayName("Scoring the winning goal transitions phase to FINISHED")
        void winningGoalTransitionsToFinished() {
            // Set P2 score to one below winning
            room.getGameState().getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal(); // P2 scores --> reaches WIN_SCORE

            loop.tick(TICK_DELTA);

            assertEquals(Phase.FINISHED, room.getPhase(),
                    "Phase should transition to FINISHED when a player reaches win score");
        }

        @Test
        @DisplayName("GameOver packet is broadcast when a player wins")
        void gameOverBroadcastOnWin() {
            room.getGameState().getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            List<GameOver> toP1 = connector.getPacketsSentToOfType(CONN_P1, GameOver.class);
            List<GameOver> toP2 = connector.getPacketsSentToOfType(CONN_P2, GameOver.class);
            assertEquals(1, toP1.size(), "Player 1 should receive GameOver");
            assertEquals(1, toP2.size(), "Player 2 should receive GameOver");
        }

        @Test
        @DisplayName("GameOver packet contains correct winner info and final score")
        void gameOverContainsCorrectWinnerInfo() {
            room.getGameState().getScore().setPlayer1Score(WIN_SCORE - 1);
            aimPuckAtPlayer2Goal(); // P1 scores --> reaches WIN_SCORE

            loop.tick(TICK_DELTA);

            GameOver packet = connector.getAllPacketsOfType(GameOver.class).get(0);
            assertEquals(CONN_P1, packet.winnerId, "Winner ID should be Player 1's connection ID");
            assertEquals(NAME_P1, packet.winnerName, "Winner name should be Player 1's name");
            assertEquals(WIN_SCORE, packet.player1Score, "Final P1 score should be win score");
            assertEquals(0, packet.player2Score, "Final P2 score should be 0");
        }

        @Test
        @DisplayName("GameOver maps winnerId and winnerName correctly when Player 2 wins")
        void gameOverContainsCorrectWinnerInfoWhenPlayer2Wins() {
            room.getGameState().getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal(); // P2 scores --> reaches WIN_SCORE

            loop.tick(TICK_DELTA);

            GameOver packet = connector.getAllPacketsOfType(GameOver.class).get(0);
            assertEquals(CONN_P2, packet.winnerId, "Winner ID should be Player 2's connection ID, not the slot number");
            assertEquals(NAME_P2, packet.winnerName, "Winner name should be Player 2's name");
            assertEquals(0, packet.player1Score, "Final P1 score should be 0");
            assertEquals(WIN_SCORE, packet.player2Score, "Final P2 score should be win score");
        }

        @Test
        @DisplayName("Game loop marks itself as not running after win condition")
        void loopStopsAfterWin() {
            room.getGameState().getScore().setPlayer2Score(WIN_SCORE - 1);
            aimPuckAtPlayer1Goal();

            loop.tick(TICK_DELTA);

            assertFalse(loop.isRunning(), "Loop should stop after game over");
        }

        @Test
        @DisplayName("checkWinCondition returns false when no winner yet")
        void checkWinConditionReturnsFalseWithNoWinner() {
            room.getGameState().getScore().setPlayer1Score(0);
            room.getGameState().getScore().setPlayer2Score(0);

            assertFalse(loop.checkWinCondition(), "Should return false when no winner");
            assertEquals(Phase.PLAYING, room.getPhase(), "Phase should still be PLAYING");
        }

        @Test
        @DisplayName("checkWinCondition is idempotent when already FINISHED")
        void checkWinConditionIdempotentWhenFinished() {
            room.getGameState().getScore().setPlayer1Score(WIN_SCORE);
            room.setPhase(Phase.FINISHED);
            connector.clear();

            assertTrue(loop.checkWinCondition(), "Should return true (game is over)");
            assertTrue(connector.getAllPacketsOfType(GameOver.class).isEmpty(),
                    "No duplicate GameOver should be broadcast");
        }
    }

    // ==================================================================
    // 7. Start / Stop lifecycle
    // ==================================================================

    @Nested
    @DisplayName("Start and stop lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("start() sets running to true")
        void startSetsRunning() {
            loop.start();
            assertTrue(loop.isRunning(), "Loop should be running after start()");
            loop.stop(); // cleanup
        }

        @Test
        @DisplayName("stop() sets running to false")
        void stopSetsNotRunning() {
            loop.start();
            loop.stop();
            assertFalse(loop.isRunning(), "Loop should not be running after stop()");
        }

        @Test
        @DisplayName("start() is idempotent - calling twice does not create two threads")
        void startIsIdempotent() {
            loop.start();
            loop.start(); // second call should be ignored
            assertTrue(loop.isRunning(), "Loop should still be running");
            loop.stop();
        }

        @Test
        @DisplayName("getInputQueue() returns the same queue passed to constructor")
        void getInputQueueReturnsConstructorQueue() {
            assertSame(inputQueue, loop.getInputQueue(),
                    "getInputQueue() should return the queue provided at construction");
        }

        @Test
        @Tag("A1")
        @DisplayName("startWithoutReset() sets running to true")
        void startWithoutResetSetsRunning() {
            // A1: Reconnecting players must be able to resume an active loop.
            loop.startWithoutReset();
            assertTrue(loop.isRunning(), "Loop should be running after startWithoutReset()");
            loop.stop();
        }

        @Test
        @Tag("A1")
        @DisplayName("startWithoutReset() does not reset puck to center")
        void startWithoutResetPreservesPuckPosition() {
            // A1: On reconnect the puck must stay in its current position so game state
            // is not corrupted for the player who stayed connected.
            float customX = 123f;
            float customY = 77f;
            room.getGameState().getPuck().setPosition(customX, customY);

            loop.startWithoutReset();

            assertEquals(customX, room.getGameState().getPuck().getX(), 0.001f,
                    "Puck X must not be reset by startWithoutReset()");
            assertEquals(customY, room.getGameState().getPuck().getY(), 0.001f,
                    "Puck Y must not be reset by startWithoutReset()");
            loop.stop();
        }

        @Test
        @Tag("A1")
        @DisplayName("startWithoutReset() preserves PAUSED phase for mid-game reconnect")
        void startWithoutResetPreservesPausedPhase() {
            // A1: If a player reconnects while the game is paused, the loop must not
            // change the phase back to PLAYING.
            room.setPhase(Phase.PAUSED);

            loop.startWithoutReset();

            assertEquals(Phase.PAUSED, room.getPhase(),
                    "Phase must remain PAUSED after startWithoutReset() on a paused room");
            loop.stop();
        }

        @Test
        @Tag("A1")
        @DisplayName("startWithoutReset() is idempotent - calling twice does not create two threads")
        void startWithoutResetIsIdempotent() {
            loop.startWithoutReset();
            loop.startWithoutReset(); // second call should be ignored
            assertTrue(loop.isRunning(), "Loop should still be running after second startWithoutReset()");
            loop.stop();
        }
    }

    // ==================================================================
    // 8. Tick constants
    // ==================================================================

    @Nested
    @DisplayName("Tick configuration constants")
    class TickConstantTests {

        @Test
        @DisplayName("Tick rate is 60 Hz")
        void tickRateIs60Hz() {
            assertEquals(60, GameLoop.TICK_RATE, "Game loop should run at 60 Hz");
        }

        @Test
        @DisplayName("Tick duration in nanoseconds matches 60 Hz")
        void tickDurationMatchesRate() {
            long expectedNs = 1_000_000_000L / 60;
            assertEquals(expectedNs, GameLoop.TICK_DURATION_NS,
                    "Tick duration should be 1/60th of a second in nanoseconds");
        }
    }
}
