package no.ntnu.ping404.server.metrics;

/**
 * Strategy interface for resolving a geo-location label from an IP address.
 * Implementations must never throw; return {@code null} when the location cannot be determined.
 * The default implementation is {@link NoOpGeoLocationService}.
 */
public interface GeoLocationService {

    /**
     * Returns a human-readable location string for the given IP address, or {@code null} if unavailable.
     */
    String lookup(String ipAddress);
}
