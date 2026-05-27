package no.ntnu.ping404.network;

/**
 * Network configuration constants.
 * Centralized configuration for both client and server.
 */
public final class NetworkConfig {

    private NetworkConfig() {
        // Prevent instantiation
    }

    // Version
    public static final String CLIENT_VERSION = "1.0.0";

    // Connection settings
    public static final int TCP_PORT = 27960;
    public static final int UDP_PORT = 27961;
    public static final String DEFAULT_HOST = "localhost";

    // Timeouts (in milliseconds)
    public static final int CONNECTION_TIMEOUT = 5000;
    public static final int KEEP_ALIVE_INTERVAL = 1000;

    // Buffer sizes
    public static final int WRITE_BUFFER_SIZE = 16384;
    public static final int OBJECT_BUFFER_SIZE = 4096;

    // Game settings
    public static final int MAX_PLAYERS = 100;
    public static final float POSITION_UPDATE_RATE = 1f / 20f; // 20 updates per second

    // Heartbeat settings (non-final to allow test overrides)
    public static int HEARTBEAT_INTERVAL_MS = 5000; // 5 seconds
    public static int HEARTBEAT_TIMEOUT_MS = 15000; // 15 seconds

    /**
     * Reset heartbeat timeouts to defaults (for test cleanup).
     */
    public static void resetHeartbeatTimeouts() {
        HEARTBEAT_INTERVAL_MS = 5000;
        HEARTBEAT_TIMEOUT_MS = 15000;
        no.ntnu.kryonet.core.NetworkConfig.HEARTBEAT_INTERVAL_MS = HEARTBEAT_INTERVAL_MS;
        no.ntnu.kryonet.core.NetworkConfig.HEARTBEAT_TIMEOUT_MS = HEARTBEAT_TIMEOUT_MS;
    }

    /**
     * Set custom heartbeat timeouts for testing.
     * Call resetHeartbeatTimeouts() after tests complete.
     */
    public static void setHeartbeatTimeouts(int intervalMs, int timeoutMs) {
        HEARTBEAT_INTERVAL_MS = intervalMs;
        HEARTBEAT_TIMEOUT_MS = timeoutMs;
        no.ntnu.kryonet.core.NetworkConfig.HEARTBEAT_INTERVAL_MS = intervalMs;
        no.ntnu.kryonet.core.NetworkConfig.HEARTBEAT_TIMEOUT_MS = timeoutMs;
    }
}
