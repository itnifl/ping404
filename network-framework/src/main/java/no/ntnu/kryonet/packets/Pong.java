package no.ntnu.kryonet.packets;

import java.util.concurrent.TimeUnit;

/** Server to client: latency measurement response. */
public class Pong {

    public long originalTimestamp;
    public long serverTimestamp;
    public int sequence;
    private transient volatile long cachedRttMs = -1L;

    public Pong() {}

    public Pong(Ping ping) {
        this.originalTimestamp = ping.timestamp;
        this.serverTimestamp = System.nanoTime();
        this.sequence = ping.sequence;
    }

    public long getRoundTripTime() {
        long local = cachedRttMs;
        if (local >= 0L) {
            return local;
        }
        synchronized (this) {
            if (cachedRttMs < 0L) {
                long elapsedNanos = System.nanoTime() - originalTimestamp;
                cachedRttMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNanos));
            }
            return cachedRttMs;
        }
    }

    @Override
    public String toString() {
        return "Pong{sequence=" + sequence + ", rtt=" + getRoundTripTime() + "ms}";
    }
}
