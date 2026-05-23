package no.ntnu.ping404.network.packets;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import no.ntnu.ping404.model.GameState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the GameStateSnapshot packet survives a full Kryo
 * serialization/deserialization round-trip with all fields intact.
 *
 * <p>Guards against Kryo registration or field-mapping regressions for
 * the puck-state broadcast packet introduced in issue #23 (FR2.3, TC3).
 */
class GameStateSnapshotTest {

    @Test
    @Tag("TC3")
    @Tag("FR2.3")
    void gameStateSnapshotFieldsSurviveKryoRoundTrip() {
        Kryo kryo = new Kryo();
        PacketRegistry.register(kryo);

        GameStateSnapshot original = new GameStateSnapshot(
            new Vector2(123.4f, 56.7f),
            new Vector2(300f, -150f),
            new Vector2(10f, 400f),
            new Vector2(790f, 400f),
            3,
            5,
            GameState.Phase.PLAYING
        );

        byte[] bytes;
        try (Output output = new Output(1024)) {
            kryo.writeObject(output, original);
            bytes = output.toBytes();
        }

        GameStateSnapshot result;
        try (Input input = new Input(bytes)) {
            result = kryo.readObject(input, GameStateSnapshot.class);
        }

        assertAll(
            () -> assertEquals(original.puckPosition.x,   result.puckPosition.x,   0.001f, "puck x must survive round-trip"),
            () -> assertEquals(original.puckPosition.y,   result.puckPosition.y,   0.001f, "puck y must survive round-trip"),
            () -> assertEquals(original.puckVelocity.x,   result.puckVelocity.x,   0.001f, "puck velocityX must survive round-trip"),
            () -> assertEquals(original.puckVelocity.y,   result.puckVelocity.y,   0.001f, "puck velocityY must survive round-trip"),
            () -> assertEquals(original.player1Position.x, result.player1Position.x, 0.001f, "player1 x must survive round-trip"),
            () -> assertEquals(original.player2Position.x, result.player2Position.x, 0.001f, "player2 x must survive round-trip"),
            () -> assertEquals(original.player1Score,     result.player1Score,     "player1Score must survive round-trip"),
            () -> assertEquals(original.player2Score,     result.player2Score,     "player2Score must survive round-trip"),
            () -> assertEquals(original.phase,            result.phase,            "phase must survive round-trip"),
            () -> assertEquals(original.timestamp,        result.timestamp,        "timestamp must survive round-trip")
        );
    }

    @Test
    @Tag("TC3")
    void defaultConstructorProducesValidInstance() {
        GameStateSnapshot packet = new GameStateSnapshot();

        assertNotNull(packet, "Default constructor must produce a non-null instance for Kryo");
        assertEquals(0, packet.player1Score, "player1Score should default to 0");
        assertEquals(0, packet.player2Score, "player2Score should default to 0");
        assertEquals(0L, packet.timestamp,   "timestamp should default to 0");
        assertNull(packet.phase,             "phase should default to null");
        assertNull(packet.puckPosition,      "puckPosition should default to null");
        assertNull(packet.puckVelocity,      "puckVelocity should default to null");
    }

    @Test
    @Tag("TC3")
    @Tag("FR2.3")
    void timestampIsSetOnConstruction() {
        long before = System.currentTimeMillis();
        GameStateSnapshot packet = new GameStateSnapshot(
            new Vector2(0, 0), new Vector2(0, 0),
            new Vector2(0, 0), new Vector2(0, 0),
            0, 0,
            GameState.Phase.PLAYING
        );
        long after = System.currentTimeMillis();

        assertTrue(packet.timestamp >= before && packet.timestamp <= after,
            "Timestamp must be set to the current time on construction");
    }
}
