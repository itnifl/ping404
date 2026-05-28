package no.creekcode.kryonet.packets;

/** Bidirectional: player position update (typically sent via UDP). */
public class PlayerPosition {

    public int playerId;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public long timestamp;

    public PlayerPosition() {}

    public PlayerPosition(int playerId, float x, float y) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.timestamp = System.currentTimeMillis();
    }
}
