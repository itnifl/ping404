package no.ntnu.kryonet.packets;

import java.util.concurrent.TimeUnit;

/** Server to client: latency measurement response. */
public class Pong {

    public long originalTimestamp;
    public long serverTimestamp;
    public int sequence;
    private transient volatile long receivedAtNanos = -1L;

    public Pong() {}

    public Pong(Ping ping) {
        this.originalTimestamp = ping.timestamp;
        this.serverTimestamp = System.nanoTime();
        this.sequence = ping.sequence;
    }

    public void markReceivedNow() {
        if (receivedAtNanos >= 0L) {
            return;
        }
        synchronized (this) {
            if (receivedAtNanos < 0L) {
                receivedAtNanos = System.nanoTime();
            }
        }
    }

    public long getRoundTripTime() {
        long baseline = receivedAtNanos >= 0L ? receivedAtNanos : System.nanoTime();
        long elapsedNanos = baseline - originalTimestamp;
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNanos));
    }

    @Override
    public String toString() {
        return "Pong{sequence=" + sequence + ", rtt=" + getRoundTripTime() + "ms}";
    }
}
