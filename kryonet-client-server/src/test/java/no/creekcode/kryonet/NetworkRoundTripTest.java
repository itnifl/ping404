package no.creekcode.kryonet;

import no.creekcode.kryonet.builder.NetworkFramework;
import no.creekcode.kryonet.core.INetworkClient;
import no.creekcode.kryonet.core.INetworkServer;
import no.creekcode.kryonet.observer.NetworkListener;
import no.creekcode.kryonet.packets.Ping;
import no.creekcode.kryonet.packets.Pong;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRoundTripTest {

    private static final int LATCH_TIMEOUT_SECONDS = 3;

    @Test
    void pingPongRoundTripWorksWithFrameworkBuilders() throws Exception {
        int tcpPort = findFreeTcpPort();
        int udpPort = findFreeUdpPort();

        INetworkServer server = NetworkFramework.serverBuilder()
                .withFrameworkPackets()
                .withFrameworkHandler(Ping.class)
                .build();

        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch pongReceived = new CountDownLatch(1);
        AtomicLong rttMs = new AtomicLong(-1L);

        INetworkClient client = NetworkFramework.clientBuilder()
                .withFrameworkPackets()
                .onPacket(Pong.class, pong -> {
                    rttMs.set(pong.getRoundTripTime());
                    pongReceived.countDown();
                })
                .addListener(new NetworkListener.Adapter() {
                    @Override
                    public void onConnected() {
                        connected.countDown();
                    }
                })
                .build();

        try {
            server.start(tcpPort, udpPort);
            client.connect("127.0.0.1", tcpPort, udpPort);

            assertTrue(connected.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Client should connect to framework server");

            client.sendUDP(new Ping(42));

            assertTrue(pongReceived.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Client should receive Pong from framework Ping handler");
            assertTrue(rttMs.get() >= 0L, "RTT in Pong should be non-negative");
        } finally {
            client.disconnect();
            client.dispose();
            server.stop();
        }
    }

    private int findFreeTcpPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Unable to allocate free TCP port", e);
        }
    }

    private int findFreeUdpPort() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Unable to allocate free UDP port", e);
        }
    }
}
