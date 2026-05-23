package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Notification that host has migrated to a new player.
 * Sent to the remaining player when the original host disconnects.
 */
public class HostMigration {

    /** Required for Kryo serialization */
    public HostMigration() {}
}
