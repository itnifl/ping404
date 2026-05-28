package no.ntnu.kryonet;

import no.ntnu.kryonet.builder.NetworkFramework;
import no.ntnu.kryonet.core.INetworkClient;
import no.ntnu.kryonet.core.INetworkServer;
import no.ntnu.kryonet.core.NetworkConfig;
import no.ntnu.kryonet.packets.Ping;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkHeartbeatLivenessTest {

    @Test
    void serverKeepsConnectionAliveWithoutExplicitPongHandler() throws Exception {
        int originalInterval = NetworkConfig.HEARTBEAT_INTERVAL_MS;
        int originalTimeout = NetworkConfig.HEARTBEAT_TIMEOUT_MS;
        int tcpPort = findFreeTcpPort();
        int udpPort = findFreeUdpPort();

        NetworkConfig.HEARTBEAT_INTERVAL_MS = 80;
        NetworkConfig.HEARTBEAT_TIMEOUT_MS = 320;

        INetworkServer server = NetworkFramework.serverBuilder()
                .withFrameworkPackets()
                .withFrameworkHandler(Ping.class)
                .build();

        INetworkClient client = NetworkFramework.clientBuilder()
                .withFrameworkPackets()
                .build();

        try {
            server.start(tcpPort, udpPort);
            client.connect("127.0.0.1", tcpPort, udpPort);

            assertTrue(awaitCondition(() -> server.getConnectionCount() == 1, 2, TimeUnit.SECONDS),
                    "Client should connect to server");

            Thread.sleep(1000L);

            assertTrue(client.isConnected(), "Client should still be connected after several heartbeat cycles");
            assertEquals(1, server.getConnectionCount(),
                    "Server should keep client alive even without explicit Pong handler registration");
        } finally {
            client.disconnect();
            client.dispose();
            server.stop();
            NetworkConfig.HEARTBEAT_INTERVAL_MS = originalInterval;
            NetworkConfig.HEARTBEAT_TIMEOUT_MS = originalTimeout;
        }
    }

    private boolean awaitCondition(Check check, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (check.evaluate()) {
                return true;
            }
            Thread.sleep(25L);
        }
        return check.evaluate();
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

    @FunctionalInterface
    private interface Check {
        boolean evaluate();
    }
}