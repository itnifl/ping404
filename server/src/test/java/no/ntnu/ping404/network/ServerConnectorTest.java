package no.ntnu.ping404.network;

import no.ntnu.ping404.network.packets.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerConnectorTest {

    private static final int CONNECTION_ID = 42;
    private static final int EXCLUDE_ID = 99;
    private static final int PLAYER_ID = 1;
    private static final float POSITION_X = 10f;
    private static final float POSITION_Y = 20f;
    private static final int PING_SEQUENCE = 7;

    private static class RecordingNetworkServer extends NetworkKryoServer {
        int sendToTcpCalls;
        int sendToUdpCalls;
        int sendToAllTcpCalls;
        int sendToAllUdpCalls;
        int sendToAllExceptTcpCalls;
        int sendToAllExceptUdpCalls;
        int lastConnectionId;
        int lastExcludeId;
        Object lastPacket;

        @Override
        public void sendToTCP(int connectionId, Object packet) {
            sendToTcpCalls++;
            lastConnectionId = connectionId;
            lastPacket = packet;
        }

        @Override
        public void sendToUDP(int connectionId, Object packet) {
            sendToUdpCalls++;
            lastConnectionId = connectionId;
            lastPacket = packet;
        }

        @Override
        public void sendToAllTCP(Object packet) {
            sendToAllTcpCalls++;
            lastPacket = packet;
        }

        @Override
        public void sendToAllUDP(Object packet) {
            sendToAllUdpCalls++;
            lastPacket = packet;
        }

        @Override
        public void sendToAllExceptTCP(int excludeConnectionId, Object packet) {
            sendToAllExceptTcpCalls++;
            lastExcludeId = excludeConnectionId;
            lastPacket = packet;
        }

        @Override
        public void sendToAllExceptUDP(int excludeConnectionId, Object packet) {
            sendToAllExceptUdpCalls++;
            lastExcludeId = excludeConnectionId;
            lastPacket = packet;
        }
    }

    // NOTE: Tests in this class require a test double for NetworkKryoServer to intercept
    // sendToTCP / sendToUDP calls. Add a minimal stub or a mocking library (e.g. Mockito)
    // when implementing these tests.

    @Test
    @Tag("TC3")
    void playerPositionPacketIsRoutedToUdp() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        PlayerPosition packet = new PlayerPosition(PLAYER_ID, POSITION_X, POSITION_Y);

        connector.send(CONNECTION_ID, packet);

        assertEquals(1, networkServer.sendToUdpCalls);
        assertEquals(0, networkServer.sendToTcpCalls);
        assertEquals(CONNECTION_ID, networkServer.lastConnectionId);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void pingPacketIsRoutedToUdp() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        Ping packet = new Ping(PING_SEQUENCE);

        connector.send(CONNECTION_ID, packet);

        assertEquals(1, networkServer.sendToUdpCalls);
        assertEquals(0, networkServer.sendToTcpCalls);
        assertEquals(CONNECTION_ID, networkServer.lastConnectionId);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void pongPacketIsRoutedToUdp() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        Pong packet = new Pong(new Ping(PING_SEQUENCE));

        connector.send(CONNECTION_ID, packet);

        assertEquals(1, networkServer.sendToUdpCalls);
        assertEquals(0, networkServer.sendToTcpCalls);
        assertEquals(CONNECTION_ID, networkServer.lastConnectionId);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void loginRequestPacketIsRoutedToTcp() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        LoginRequest packet = new LoginRequest();

        connector.send(CONNECTION_ID, packet);

        assertEquals(1, networkServer.sendToTcpCalls);
        assertEquals(0, networkServer.sendToUdpCalls);
        assertEquals(CONNECTION_ID, networkServer.lastConnectionId);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void chatMessagePacketIsRoutedToTcp() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        ChatMessage packet = new ChatMessage();

        connector.send(CONNECTION_ID, packet);

        assertEquals(1, networkServer.sendToTcpCalls);
        assertEquals(0, networkServer.sendToUdpCalls);
        assertEquals(CONNECTION_ID, networkServer.lastConnectionId);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void broadcastSendsToAllConnectionsViaTcpForCriticalPackets() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        LoginResponse packet = new LoginResponse();

        connector.broadcast(packet);

        assertEquals(1, networkServer.sendToAllTcpCalls);
        assertEquals(0, networkServer.sendToAllUdpCalls);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void broadcastSendsToAllConnectionsViaUdpForUnreliablePackets() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        PlayerPosition packet = new PlayerPosition(PLAYER_ID, POSITION_X, POSITION_Y);

        connector.broadcast(packet);

        assertEquals(1, networkServer.sendToAllUdpCalls);
        assertEquals(0, networkServer.sendToAllTcpCalls);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void broadcastExceptExcludesSpecifiedConnectionForTcp() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        LoginResponse packet = new LoginResponse();

        connector.broadcastExcept(EXCLUDE_ID, packet);

        assertEquals(1, networkServer.sendToAllExceptTcpCalls);
        assertEquals(0, networkServer.sendToAllExceptUdpCalls);
        assertEquals(EXCLUDE_ID, networkServer.lastExcludeId);
        assertSame(packet, networkServer.lastPacket);
    }

    @Test
    @Tag("TC3")
    void broadcastExceptExcludesSpecifiedConnectionForUdpUnreliablePackets() {
        RecordingNetworkServer networkServer = new RecordingNetworkServer();
        ServerConnector connector = new ServerConnector(networkServer);
        PlayerPosition packet = new PlayerPosition(PLAYER_ID, POSITION_X, POSITION_Y);

        connector.broadcastExcept(EXCLUDE_ID, packet);

        assertEquals(1, networkServer.sendToAllExceptUdpCalls);
        assertEquals(0, networkServer.sendToAllExceptTcpCalls);
        assertEquals(EXCLUDE_ID, networkServer.lastExcludeId);
        assertSame(packet, networkServer.lastPacket);
    }
}
