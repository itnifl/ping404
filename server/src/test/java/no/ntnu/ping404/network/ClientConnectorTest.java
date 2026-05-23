package no.ntnu.ping404.network;

import no.ntnu.ping404.network.packets.ChatMessage;
import no.ntnu.ping404.network.packets.Ping;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.network.packets.Pong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientConnectorTest {

    private static final int PLAYER_ID = 1;
    private static final float POSITION_X = 10f;
    private static final float POSITION_Y = 20f;
    private static final int PING_SEQUENCE = 7;
    private static final String CHAT_SENDER_NAME = "Alice";
    private static final String CHAT_MESSAGE = "Hi";

    private static class RecordingNetworkClient extends NetworkKryoClient {
        int sendTcpCalls;
        int sendUdpCalls;
        Object lastPacket;

        @Override
        public void sendTCP(Object packet) {
            sendTcpCalls++;
            lastPacket = packet;
        }

        @Override
        public void sendUDP(Object packet) {
            sendUdpCalls++;
            lastPacket = packet;
        }
    }

    @Test
    @Tag("TC3")
    void playerPositionPacketIsRoutedToUdp() {
        RecordingNetworkClient networkClient = new RecordingNetworkClient();
        ClientConnector connector = new ClientConnector(networkClient);
        PlayerPosition packet = new PlayerPosition(PLAYER_ID, POSITION_X, POSITION_Y);

        connector.send(packet);

        assertEquals(1, networkClient.sendUdpCalls);
        assertEquals(0, networkClient.sendTcpCalls);
        assertSame(packet, networkClient.lastPacket);
    }

    @Test
    @Tag("TC3")
    void pingPacketIsRoutedToUdp() {
        RecordingNetworkClient networkClient = new RecordingNetworkClient();
        ClientConnector connector = new ClientConnector(networkClient);
        Ping packet = new Ping(PING_SEQUENCE);

        connector.send(packet);

        assertEquals(1, networkClient.sendUdpCalls);
        assertEquals(0, networkClient.sendTcpCalls);
        assertSame(packet, networkClient.lastPacket);
    }

    @Test
    @Tag("TC3")
    void pongPacketIsRoutedToUdp() {
        RecordingNetworkClient networkClient = new RecordingNetworkClient();
        ClientConnector connector = new ClientConnector(networkClient);
        Pong packet = new Pong(new Ping(PING_SEQUENCE));

        connector.send(packet);

        assertEquals(1, networkClient.sendUdpCalls);
        assertEquals(0, networkClient.sendTcpCalls);
        assertSame(packet, networkClient.lastPacket);
    }

    @Test
    @Tag("TC3")
    void chatMessagePacketIsRoutedToTcp() {
        RecordingNetworkClient networkClient = new RecordingNetworkClient();
        ClientConnector connector = new ClientConnector(networkClient);
        ChatMessage packet = ChatMessage.player(PLAYER_ID, CHAT_SENDER_NAME, CHAT_MESSAGE);

        connector.send(packet);

        assertEquals(0, networkClient.sendUdpCalls);
        assertEquals(1, networkClient.sendTcpCalls);
        assertSame(packet, networkClient.lastPacket);
    }
}
