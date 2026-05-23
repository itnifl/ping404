package no.ntnu.ping404.network.packets;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kryo round-trip tests for GameStartRequest and GameStartEvent packets.
 */
class GameStartPacketTest {

    @Test
    @Tag("FR2.1")
    @DisplayName("GameStartRequest survives Kryo round-trip")
    void gameStartRequestSurvivesKryoRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        GameStartRequest original = new GameStartRequest(42);

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        GameStartRequest result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, GameStartRequest.class);
        }

        assertEquals(42, result.requesterId, "requesterId should survive round-trip");
    }

    @Test
    @Tag("FR2.1")
    @DisplayName("GameStartEvent survives Kryo round-trip with all fields")
    void gameStartEventSurvivesKryoRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        GameStartEvent original = new GameStartEvent(
                1, 2, 1, "Alice", "Bob", 7
        );

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        GameStartEvent result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, GameStartEvent.class);
        }

        assertEquals(1, result.playerId, "playerId should survive");
        assertEquals(2, result.opponentId, "opponentId should survive");
        assertEquals(1, result.playerSlot, "playerSlot should survive");
        assertEquals("Alice", result.playerName, "playerName should survive");
        assertEquals("Bob", result.opponentName, "opponentName should survive");
        assertEquals(7, result.winScore, "winScore should survive");
    }

    @Test
    @DisplayName("GameStartEvent default constructor works for Kryo")
    void gameStartEventDefaultConstructor() {
        GameStartEvent event = new GameStartEvent();
        assertNotNull(event);
        assertEquals(0, event.playerId);
        assertNull(event.playerName);
    }

    @Test
    @DisplayName("GameStartRequest default constructor works for Kryo")
    void gameStartRequestDefaultConstructor() {
        GameStartRequest request = new GameStartRequest();
        assertNotNull(request);
        assertEquals(0, request.requesterId);
    }
}
