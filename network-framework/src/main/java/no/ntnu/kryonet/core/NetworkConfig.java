package no.ntnu.kryonet.core;

/** Framework-level network constants. Games override ports and buffer sizes via the builders. */
public final class NetworkConfig {

    private NetworkConfig() {}

    public static final int DEFAULT_TCP_PORT = 27960;
    public static final int DEFAULT_UDP_PORT = 27961;
    public static final String DEFAULT_HOST = "localhost";

    public static final int CONNECTION_TIMEOUT_MS = 5000;

    public static final int WRITE_BUFFER_SIZE = 16384;
    public static final int OBJECT_BUFFER_SIZE = 4096;

    public static volatile int HEARTBEAT_INTERVAL_MS = 5000;
    public static volatile int HEARTBEAT_TIMEOUT_MS  = 15000;
}
