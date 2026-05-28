package no.ntnu.kryonet.packets;

/** Client to server: latency measurement request. */
public class Ping {

    public long timestamp;
    public int sequence;

    public Ping() {}

    public Ping(int sequence) {
        this.timestamp = System.nanoTime();
        this.sequence = sequence;
    }

    @Override
    public String toString() {
        return "Ping{sequence=" + sequence + "}";
    }
}
