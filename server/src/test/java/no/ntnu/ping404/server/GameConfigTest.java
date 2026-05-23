package no.ntnu.ping404.server;

import no.ntnu.ping404.network.GameConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GameConfig, verifying configuration loading and defaults (FR1.6, M3).
 */
class GameConfigTest {

    private static final int DEFAULT_WIN_SCORE = 5;
    private static final int CUSTOM_WIN_SCORE = 10;
    private static final int DEFAULT_SIMULATION_TICK_HZ = 60;
    private static final int DEFAULT_MAX_BROADCAST_HZ = 20;
    private static final double DEFAULT_MAX_LOOP_JITTER_MS = 10.0;

    @BeforeEach
    void setUp() {
        GameConfig.reset();
    }

    @AfterEach
    void tearDown() {
        GameConfig.reset();
    }

    @Test
    @Tag("FR1.6")
    @Tag("M3")
    @DisplayName("Default win score is 5")
    void defaultWinScoreIsFive() {
        assertEquals(DEFAULT_WIN_SCORE, GameConfig.getWinScore(),
                "Default win score should be 5");
    }

    @Test
    @Tag("M3")
    @DisplayName("Win score can be overridden for testing")
    void winScoreCanBeOverridden() {
        GameConfig.setWinScore(CUSTOM_WIN_SCORE);
        assertEquals(CUSTOM_WIN_SCORE, GameConfig.getWinScore());
    }

    @Test
    @Tag("M3")
    @DisplayName("Reset restores default value")
    void resetRestoresDefaults() {
        GameConfig.setWinScore(CUSTOM_WIN_SCORE);
        GameConfig.reset();
        assertEquals(DEFAULT_WIN_SCORE, GameConfig.getWinScore());
    }

    @Test
    @Tag("M3")
    @DisplayName("Configuration is loaded from properties file if present")
    void configurationIsLoadedFromPropertiesFile() {
        GameConfig.load();
        assertTrue(GameConfig.getWinScore() >= 1,
                "Win score should be a positive value");
    }

    @Test
    @Tag("M3")
    @DisplayName("Default runtime targets are available")
    void defaultRuntimeTargetsAreAvailable() {
        assertEquals(DEFAULT_SIMULATION_TICK_HZ, GameConfig.getSimulationTickHz(),
                "Default simulation tick should be 60 Hz");
        assertEquals(DEFAULT_MAX_BROADCAST_HZ, GameConfig.getMaxStateBroadcastHz(),
                "Default max broadcast should be 20 Hz");
        assertEquals(DEFAULT_MAX_LOOP_JITTER_MS, GameConfig.getMaxLoopJitterMs(), 0.001,
                "Default max jitter should be 10 ms");
    }

    @Test
    @Tag("M3")
    @DisplayName("Runtime targets can be overridden for testing")
    void runtimeTargetsCanBeOverridden() {
        GameConfig.setRuntimeTargets(120, 30, 6.5);

        assertEquals(120, GameConfig.getSimulationTickHz(), "Simulation tick should match override");
        assertEquals(30, GameConfig.getMaxStateBroadcastHz(), "Broadcast target should match override");
        assertEquals(6.5, GameConfig.getMaxLoopJitterMs(), 0.001, "Loop jitter should match override");
    }
}
