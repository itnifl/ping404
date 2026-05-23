package no.ntnu.ping404.network.packets;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the GameOver packet survives a full Kryo serialization/deserialization round-trip
 * with all fields intact. This guards against Kryo registration or field-mapping regressions.
 */
class GameOverPacketTest {

    @Test
    @Tag("TC4")
    void gameOverFieldsSurviveKryoRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        GameOver original = new GameOver(42, "Alice", 7, 3);

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        GameOver result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, GameOver.class);
        }

        assertAll(
            () -> assertEquals(original.winnerId,     result.winnerId,     "winnerId must survive round-trip"),
            () -> assertEquals(original.winnerName,   result.winnerName,   "winnerName must survive round-trip"),
            () -> assertEquals(original.player1Score, result.player1Score, "player1Score must survive round-trip"),
            () -> assertEquals(original.player2Score, result.player2Score, "player2Score must survive round-trip")
        );
    }

    @Test
    @Tag("TC4")
    void gameOverDefaultConstructorProducesValidInstance() {
        GameOver packet = new GameOver();

        assertNotNull(packet, "Default constructor must produce a non-null instance for Kryo");
        assertEquals(0, packet.winnerId,       "winnerId should default to 0");
        assertNull(packet.winnerName,           "winnerName should default to null");
        assertEquals(0, packet.player1Score,   "player1Score should default to 0");
        assertEquals(0, packet.player2Score,   "player2Score should default to 0");
    }

    @Test
    @Tag("TC4")
    void gameOverZeroScoresRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        GameOver original = new GameOver(1, "Player 1", 0, 0);

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        GameOver result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, GameOver.class);
        }

        assertEquals(0, result.player1Score, "Zero score must survive round-trip");
        assertEquals(0, result.player2Score, "Zero score must survive round-trip");
    }
}
