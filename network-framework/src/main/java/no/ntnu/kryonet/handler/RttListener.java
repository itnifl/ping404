package no.ntnu.kryonet.handler;

/**
 * Callback invoked by {@link PongHandlerCommand} when a Pong is received.
 * Implement this to record round-trip time in a metrics system.
 */
@FunctionalInterface
public interface RttListener {

    /**
     * @param connectionId the connection that sent the Pong
     * @param rttMs        round-trip time in milliseconds
     */
    void onRtt(int connectionId, long rttMs);
}
