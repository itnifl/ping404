package no.ntnu.ping404.network.packets;

/**
 * Server -> Client: Per-room runtime metrics snapshot.
 *
 * <p>Broadcast periodically to room participants so clients can display
 * server-side health alongside local client-derived metrics.</p>
 */
public class RoomMetricsSnapshot {

    /** Room identifier this snapshot belongs to. */
    public String roomId;

    /** Server timestamp when snapshot was created. */
    public long timestamp;

    /** Configured simulation tick rate for this room. */
    public int simulationTickHz;

    /** Configured cap for state broadcast frequency. */
    public int maxStateBroadcastHz;

    /** Measured state snapshot broadcast frequency. */
    public float effectiveStateBroadcastHz;

    /** Current loop jitter in milliseconds. */
    public float loopJitterMs;

    /** Maximum acceptable loop jitter in milliseconds. */
    public float maxLoopJitterMs;

    /** Position update processing rate measured by the server metrics pipeline. */
    public float incomingUpdateRateHz;

    /** Incoming position-update drop rate in range [0.0, 1.0]. */
    public float incomingDropRate;

    /** Outgoing packet drop rate in range [0.0, 1.0]. */
    public float outgoingDropRate;

    /** Outgoing bandwidth estimate in bytes/sec. */
    public float outgoingBandwidthBps;

    /** Depth of the server metrics ingestion queue. */
    public int incomingQueueDepth;

    /** Required for Kryo serialization. */
    public RoomMetricsSnapshot() {
    }

    public RoomMetricsSnapshot(
            String roomId,
            int simulationTickHz,
            int maxStateBroadcastHz,
            float effectiveStateBroadcastHz,
            float loopJitterMs,
            float maxLoopJitterMs,
            float incomingUpdateRateHz,
            float incomingDropRate,
            float outgoingDropRate,
            float outgoingBandwidthBps,
            int incomingQueueDepth) {
        this.roomId = roomId;
        this.timestamp = System.currentTimeMillis();
        this.simulationTickHz = Math.max(1, simulationTickHz);
        this.maxStateBroadcastHz = Math.max(1, maxStateBroadcastHz);
        this.effectiveStateBroadcastHz = Math.max(0f, effectiveStateBroadcastHz);
        this.loopJitterMs = Math.max(0f, loopJitterMs);
        this.maxLoopJitterMs = Math.max(0f, maxLoopJitterMs);
        this.incomingUpdateRateHz = Math.max(0f, incomingUpdateRateHz);
        this.incomingDropRate = Math.max(0f, Math.min(1f, incomingDropRate));
        this.outgoingDropRate = Math.max(0f, Math.min(1f, outgoingDropRate));
        this.outgoingBandwidthBps = Math.max(0f, outgoingBandwidthBps);
        this.incomingQueueDepth = Math.max(0, incomingQueueDepth);
    }
}
