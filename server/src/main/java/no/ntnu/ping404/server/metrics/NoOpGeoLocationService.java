package no.ntnu.ping404.server.metrics;

/**
 * No-operation {@link GeoLocationService} that always returns {@code null}.
 * Used as the default when no real geo-location provider is configured, ensuring
 * metrics collection never fails due to a missing external dependency.
 */
public class NoOpGeoLocationService implements GeoLocationService {

    @Override
    public String lookup(String ipAddress) {
        return null;
    }
}
