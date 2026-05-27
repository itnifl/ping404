package no.ntnu.kryonet.packets;

/** Server to client: latency measurement response. */
public class Pong {

    public long originalTimestamp;
    public long serverTimestamp;
    public int sequence;

    public Pong() {}

    public Pong(Ping ping) {
        this.originalTimestamp = ping.timestamp;
        this.serverTimestamp = System.currentTimeMillis();
        this.sequence = ping.sequence;
    }

    public long getRoundTripTime() {
        return System.currentTimeMillis() - originalTimestamp;
    }

    @Override
    public String toString() {
        return "Pong{sequence=" + sequence + ", rtt=" + getRoundTripTime() + "ms}";
    }
}
