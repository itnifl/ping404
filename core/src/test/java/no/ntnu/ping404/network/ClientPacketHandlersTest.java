package no.ntnu.ping404.network;

import com.badlogic.gdx.math.Vector2;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.packets.ErrorPacket;
import no.ntnu.ping404.network.packets.GameOver;
import no.ntnu.ping404.network.packets.GameStateSnapshot;
import no.ntnu.ping404.network.packets.GoalScored;
import no.ntnu.ping404.network.packets.PauseEvent;
import no.ntnu.ping404.network.packets.PlayerJoined;
import no.ntnu.ping404.network.packets.PlayerLeft;
import no.ntnu.ping404.network.packets.ResumeEvent;
import no.ntnu.ping404.network.packets.RoomMetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientPacketHandlers - typed packet handling (issue #48).
 * Tests handler logic without LibGDX dependencies.
 */
class ClientPacketHandlersTest {

    private GameScreenState state;

    @BeforeEach
    void setUp() {
        state = new GameScreenState();
    }

    @Nested
    @DisplayName("PauseEvent handling")
    class PauseEventHandling {

        @Test
        @Tag("FR2.8")
        @DisplayName("PauseEvent sets paused state and phase")
        void pauseEvent_setsPausedState() {
            PauseEvent packet = new PauseEvent(42);

            ClientPacketHandlers.handlePauseEvent(state, packet);

            assertTrue(state.isPaused(), "Should be paused");
            assertEquals(42, state.getPausedByPlayerId(), "Should record who paused");
            assertEquals(GameState.Phase.PAUSED, state.getPhase(), "Phase should be PAUSED");
        }
    }

    @Nested
    @DisplayName("ResumeEvent handling")
    class ResumeEventHandling {

        @Test
        @Tag("FR2.8")
        @DisplayName("ResumeEvent clears paused state")
        void resumeEvent_clearsPausedState() {
            state.setPaused(true);
            state.setPausedByPlayerId(42);
            state.setPhase(GameState.Phase.PAUSED);

            ResumeEvent packet = new ResumeEvent(42);
            ClientPacketHandlers.handleResumeEvent(state, packet);

            assertFalse(state.isPaused(), "Should not be paused");
            assertEquals(-1, state.getPausedByPlayerId(), "Paused-by should be cleared");
            assertEquals(GameState.Phase.PLAYING, state.getPhase(), "Phase should be PLAYING");
        }
    }

    @Nested
    @DisplayName("ErrorPacket handling")
    class ErrorPacketHandling {

        @Test
        @DisplayName("ErrorPacket sets error message and flash timer")
        void errorPacket_setsErrorState() {
            ErrorPacket packet = new ErrorPacket(42, "Only host can pause");
            ClientPacketHandlers.handleErrorPacket(state, packet);

            assertEquals("Only host can pause", state.getErrorMessage());
            assertEquals(1.0f, state.getErrorFlashTimer(), 0.001f);
        }

        @Test
        @DisplayName("ErrorPacket with null message uses default")
        void errorPacket_nullMessageUsesDefault() {
            ErrorPacket packet = new ErrorPacket(42, null);
            ClientPacketHandlers.handleErrorPacket(state, packet);

            assertEquals("Action denied", state.getErrorMessage());
            assertEquals(1.0f, state.getErrorFlashTimer(), 0.001f);
        }
    }

    @Nested
    @DisplayName("GoalScored handling")
    class GoalScoredHandling {

        @Test
        @Tag("FR3.1")
        @DisplayName("GoalScored updates scores and triggers flash")
        void goalScored_updatesScoresAndFlash() {
            GoalScored packet = new GoalScored(1, 3, 2);

            ClientPacketHandlers.handleGoalScored(state, packet);

            assertEquals(3, state.getPlayer1Score(), "Player 1 score should be updated");
            assertEquals(2, state.getPlayer2Score(), "Player 2 score should be updated");
            assertTrue(state.getGoalFlashTimer() > 0, "Goal flash timer should be set");
            assertFalse(state.getGoalMessage().isEmpty(), "Goal message should be set");
        }

        @Test
        @DisplayName("GoalScored message indicates scoring player")
        void goalScored_messageIndicatesScorer() {
            GoalScored packet = new GoalScored(2, 1, 1);

            ClientPacketHandlers.handleGoalScored(state, packet);

            assertTrue(state.getGoalMessage().contains("Player 2"), "Message should mention scoring player");
        }
    }

    @Nested
    @DisplayName("GameStateSnapshot handling")
    class GameStateSnapshotHandling {

        @Test
        @Tag("FR4.1")
        @DisplayName("GameStateSnapshot updates all positions")
        void snapshot_updatesPositions() {
            GameStateSnapshot packet = new GameStateSnapshot(
                    new Vector2(100, 200),  // puck
                    new Vector2(5, -3),      // puck velocity
                    new Vector2(50, 100),   // player 1
                    new Vector2(150, 300),  // player 2
                    2, 3,
                    GameState.Phase.PLAYING
            );

            ClientPacketHandlers.handleGameStateSnapshot(state, packet);

            assertEquals(100, state.getPuckPosition().x, 0.01f);
            assertEquals(200, state.getPuckPosition().y, 0.01f);
            assertEquals(5, state.getPuckVelocity().x, 0.01f);
            assertEquals(-3, state.getPuckVelocity().y, 0.01f);
            assertEquals(50, state.getPlayer1Position().x, 0.01f);
            assertEquals(100, state.getPlayer1Position().y, 0.01f);
            assertEquals(150, state.getPlayer2Position().x, 0.01f);
            assertEquals(300, state.getPlayer2Position().y, 0.01f);
            assertEquals(2, state.getPlayer1Score());
            assertEquals(3, state.getPlayer2Score());
            assertEquals(GameState.Phase.PLAYING, state.getPhase());
        }

        @Test
        @DisplayName("GameStateSnapshot sets paused from PAUSED phase")
        void snapshot_pausedPhase_setsPaused() {
            GameStateSnapshot packet = new GameStateSnapshot(
                    new Vector2(0, 0), new Vector2(0, 0),
                    new Vector2(0, 0), new Vector2(0, 0),
                    0, 0, GameState.Phase.PAUSED
            );

            ClientPacketHandlers.handleGameStateSnapshot(state, packet);

            assertTrue(state.isPaused());
            assertEquals(GameState.Phase.PAUSED, state.getPhase());
        }

        @Test
        @DisplayName("GameStateSnapshot handles null fields gracefully")
        void snapshot_nullFields_noException() {
            GameStateSnapshot packet = new GameStateSnapshot();
            // All fields null

            assertDoesNotThrow(() -> ClientPacketHandlers.handleGameStateSnapshot(state, packet));
        }
    }

    @Nested
    @DisplayName("RoomMetricsSnapshot handling")
    class RoomMetricsSnapshotHandling {

        @Test
        @DisplayName("RoomMetricsSnapshot stores latest server metrics")
        void roomMetricsSnapshot_updatesState() {
            RoomMetricsSnapshot packet = new RoomMetricsSnapshot(
                    "room-A",
                    60,
                    20,
                    19.5f,
                    3.2f,
                    10.0f,
                    58.0f,
                    0.01f,
                    0.02f,
                    12000f,
                    4
            );

            ClientPacketHandlers.handleRoomMetricsSnapshot(state, packet);

            assertNotNull(state.getRoomMetricsSnapshot(), "Metrics snapshot should be available in state");
            assertEquals("room-A", state.getRoomMetricsSnapshot().roomId);
            assertEquals(60, state.getRoomMetricsSnapshot().simulationTickHz);
            assertEquals(20, state.getRoomMetricsSnapshot().maxStateBroadcastHz);
        }
    }

    @Nested
    @DisplayName("PlayerJoined handling")
    class PlayerJoinedHandling {

        @Test
        @Tag("FR1.5")
        @DisplayName("PlayerJoined updates opponent info")
        void playerJoined_updatesOpponentInfo() {
            PlayerJoined packet = new PlayerJoined(2, "Bob", 100, 200);

            ClientPacketHandlers.handlePlayerJoined(state, packet);

            assertEquals("Bob", state.getOpponentName());
            assertTrue(state.isOpponentConnected());
            assertTrue(state.getStatusMessage().contains("Bob"));
            assertTrue(state.getStatusMessage().contains("joined"));
        }
    }

    @Nested
    @DisplayName("PlayerLeft handling")
    class PlayerLeftHandling {

        @Test
        @Tag("FR1.5")
        @DisplayName("PlayerLeft marks opponent disconnected")
        void playerLeft_marksDisconnected() {
            state.setOpponentConnected(true);
            PlayerLeft packet = new PlayerLeft(2, "Bob");

            ClientPacketHandlers.handlePlayerLeft(state, packet);

            assertFalse(state.isOpponentConnected());
            assertEquals("Other player disconnected!", state.getStatusMessage());
            assertEquals("", state.getDisconnectReason());
        }

        @Test
        @DisplayName("PlayerLeft stores reason if provided")
        void playerLeft_storesReason() {
            PlayerLeft packet = new PlayerLeft(2, "Bob", "connection lost");

            ClientPacketHandlers.handlePlayerLeft(state, packet);

            assertEquals("Other player disconnected!", state.getStatusMessage());
            assertEquals("connection lost", state.getDisconnectReason());
        }
    }

    @Nested
    @DisplayName("GameOver handling")
    class GameOverHandling {

        @Test
        @Tag("FR3.2")
        @DisplayName("GameOver sets final scores, FINISHED phase, and winner name")
        void gameOver_setsFinalStateAndWinnerName() {
            GameOver packet = new GameOver(1, "Alice", 5, 3);

            ClientPacketHandlers.handleGameOver(state, packet);

            assertEquals(5, state.getPlayer1Score());
            assertEquals(3, state.getPlayer2Score());
            assertEquals(GameState.Phase.FINISHED, state.getPhase());
            assertEquals("Alice", state.getWinnerName());
        }

        @Test
        @DisplayName("GameOver stores null winner name when not provided")
        void gameOver_nullWinnerName_storedAsNull() {
            GameOver packet = new GameOver(0, null, 2, 4);

            ClientPacketHandlers.handleGameOver(state, packet);

            assertNull(state.getWinnerName());
            assertEquals(GameState.Phase.FINISHED, state.getPhase());
        }
    }

    @Nested
    @DisplayName("Dispatcher factory")
    class DispatcherFactory {

        @Test
        @Tag("FR4.2")
        @DisplayName("createGameDispatcher registers all required packet types")
        void createGameDispatcher_registersAllTypes() {
            ClientPacketDispatcher dispatcher = ClientPacketHandlers.createGameDispatcher(state, null);

            assertTrue(dispatcher.hasHandler(PauseEvent.class), "Should handle PauseEvent");
            assertTrue(dispatcher.hasHandler(ResumeEvent.class), "Should handle ResumeEvent");
            assertTrue(dispatcher.hasHandler(GoalScored.class), "Should handle GoalScored");
            assertTrue(dispatcher.hasHandler(GameStateSnapshot.class), "Should handle GameStateSnapshot");
            assertTrue(dispatcher.hasHandler(RoomMetricsSnapshot.class), "Should handle RoomMetricsSnapshot");
            assertTrue(dispatcher.hasHandler(GameOver.class), "Should handle GameOver");
            assertTrue(dispatcher.hasHandler(PlayerJoined.class), "Should handle PlayerJoined");
            assertTrue(dispatcher.hasHandler(PlayerLeft.class), "Should handle PlayerLeft");
            assertTrue(dispatcher.hasHandler(ErrorPacket.class), "Should handle ErrorPacket");
        }
    }

    @Nested
    @DisplayName("Client-side metric calculations")
    class ClientSideMetricCalculations {

        @Test
        @DisplayName("Client RTT is updated from measured round-trip time")
        void clientRtt_updatesFromMeasurement() {
            state.updateClientRtt(42L);
            assertEquals(42L, state.getClientRttMs());

            state.updateClientRtt(-9L);
            assertEquals(0L, state.getClientRttMs(), "Negative RTT should be clamped to zero");
        }

        @Test
        @DisplayName("Snapshot arrival updates client receive rate and jitter")
        void snapshotArrival_updatesRateAndJitter() {
            long base = 1_000_000_000L;
            state.recordSnapshotArrival(base);
            state.recordSnapshotArrival(base + 50_000_000L);  // 20 Hz
            state.recordSnapshotArrival(base + 110_000_000L); // jitter introduced

            assertTrue(state.getClientSnapshotRateHz() > 0f, "Snapshot rate should be computed");
            assertTrue(state.getClientSnapshotJitterMs() >= 0f, "Snapshot jitter should be computed");
        }
    }
}
