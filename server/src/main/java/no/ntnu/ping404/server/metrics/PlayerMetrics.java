package no.ntnu.ping404.server.metrics;

/**
 * Holds per-player diagnostic state for a single connection within a game room.
 * Stores the player's IP address, optional geo-location label, and the most recently
 * measured Ping-Pong round-trip time. Instances are created on first contact and
 * retained for the lifetime of the room.
 */
public class PlayerMetrics {

    private final int connectionId;
    private String ipAddress;
    private String geoLocation;
    private long latestRttMs;

    public PlayerMetrics(int connectionId) {
        this.connectionId = connectionId;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getGeoLocation() {
        return geoLocation;
    }

    public long getLatestRttMs() {
        return latestRttMs;
    }

    void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    void setGeoLocation(String geoLocation) {
        this.geoLocation = geoLocation;
    }

    void setLatestRttMs(long latestRttMs) {
        this.latestRttMs = latestRttMs;
    }
}
