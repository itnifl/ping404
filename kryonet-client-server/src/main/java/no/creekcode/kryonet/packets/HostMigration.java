package no.creekcode.kryonet.packets;

/** Server to client: the host role has moved to another player. */
public class HostMigration {

    public int newHostPlayerId;
    public String newHostPlayerName;

    public HostMigration() {}

    public HostMigration(int newHostPlayerId, String newHostPlayerName) {
        this.newHostPlayerId = newHostPlayerId;
        this.newHostPlayerName = newHostPlayerName;
    }

    public int getNewHostPlayerId() {
        return newHostPlayerId;
    }

    public void setNewHostPlayerId(int newHostPlayerId) {
        this.newHostPlayerId = newHostPlayerId;
    }
}
