package no.creekcode.kryonet;

import no.creekcode.kryonet.packets.Ping;
import no.creekcode.kryonet.packets.Pong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PongTest {

    @Test
    void roundTripTimeIsStableAfterReceiveIsMarked() throws Exception {
        Ping ping = new Ping(1);
        Pong pong = new Pong(ping);

        Thread.sleep(15L);
        pong.markReceivedNow();
        long first = pong.getRoundTripTime();

        Thread.sleep(15L);
        long second = pong.getRoundTripTime();

        assertEquals(first, second, "RTT should stay stable once receive time is captured");
        assertTrue(second >= 0L, "RTT should be non-negative");
    }
}