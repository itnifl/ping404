package no.ntnu.ping404.network;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Game configuration loaded from server.properties file.
 * Falls back to defaults if properties file is missing or values are invalid.
 */
public final class GameConfig {

    private static final String PROPERTIES_FILE = "server.properties";

    // Win score setting
    private static int winScore = GameConfigDefaults.DefaultWinScore; // Default value

    private static boolean metricsEnabled = true; // Default value
    private static int simulationTickHz = 60;
    private static int maxStateBroadcastHz = 20;
    private static double maxLoopJitterMs = 10.0;

    private static boolean loaded = false;

    private static final Logger logger = LoggerFactory.getLogger(GameConfig.class);

    private GameConfig() {
        // Prevent instantiation
    }

    /**
     * Load configuration from server.properties file.
     * Called automatically on first access, but can be called explicitly.
     */
    public static synchronized void load() {
        if (loaded) return;
        
        Properties props = new Properties();
        try (InputStream input = GameConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                props.load(input);
                winScore = getIntProperty(props, "game.winScore", GameConfigDefaults.DefaultWinScore);
                metricsEnabled = getBooleanProperty(props, "metrics.enabled", true);
                simulationTickHz = getIntProperty(props, "game.simulationTickHz", 60);
                maxStateBroadcastHz = getIntProperty(props, "game.maxStateBroadcastHz", 20);
                maxLoopJitterMs = getDoubleProperty(props, "game.maxLoopJitterMs", 10.0);
                logger.info("GameConfig loaded from " + PROPERTIES_FILE);
            } else {
                logger.warn("GameConfig: " + PROPERTIES_FILE + " not found, using defaults");
            }
        } catch (IOException e) {
            logger.error("GameConfig: Error loading " + PROPERTIES_FILE + ", using defaults: " + e.getMessage(), e);
        }
        loaded = true;
    }

    private static int getIntProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("GameConfig: Invalid integer for " + key + "='" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }

    private static double getDoubleProperty(Properties props, String key, double defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0.0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("GameConfig: Invalid double for " + key + "='" + value + "', using default: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * Reset configuration to defaults (for testing).
     */
    public static synchronized void reset() {
        winScore = GameConfigDefaults.DefaultWinScore;
        metricsEnabled = true;
        simulationTickHz = 60;
        maxStateBroadcastHz = 20;
        maxLoopJitterMs = 10.0;
        loaded = false;
        logger.info("GameConfig reset to defaults.");
    }

    /**
     * Set custom win score (for testing).
     */
    public static synchronized void setWinScore(int score) {
        winScore = score;
        loaded = true;
    }

    /**
     * Set runtime performance targets (for testing).
     */
    public static synchronized void setRuntimeTargets(int tickHz, int broadcastHz, double jitterMs) {
        simulationTickHz = tickHz > 0 ? tickHz : 60;
        maxStateBroadcastHz = broadcastHz > 0 ? broadcastHz : 20;
        maxLoopJitterMs = jitterMs > 0 ? jitterMs : 10.0;
        loaded = true;
    }

    /**
     * Get configured win score.
     */
    public static int getWinScore() {
        ensureLoaded();
        return winScore;
    }

    /**
     * Returns whether per-room metrics collection is enabled.
     */
    public static boolean isMetricsEnabled() {
        ensureLoaded();
        return metricsEnabled;
    }

    /** Returns target simulation tick rate for each room. */
    public static int getSimulationTickHz() {
        ensureLoaded();
        return simulationTickHz;
    }

    /** Returns maximum broadcast frequency for state updates per room. */
    public static int getMaxStateBroadcastHz() {
        ensureLoaded();
        return maxStateBroadcastHz;
    }

    /** Returns maximum acceptable loop jitter in milliseconds. */
    public static double getMaxLoopJitterMs() {
        ensureLoaded();
        return maxLoopJitterMs;
    }

    private static boolean getBooleanProperty(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }
}