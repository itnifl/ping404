package no.ntnu.kryonet.registry;

import com.esotericsoftware.kryo.Kryo;
import no.ntnu.kryonet.packets.*;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Registers all framework-supplied packet types with a Kryo instance.
 *
 * <p>Always call this before registering user-defined packets so that the
 * auto-assigned Kryo IDs are stable across all users of the framework.</p>
 */
public final class FrameworkPacketRegistry {

    private FrameworkPacketRegistry() {}

    public static void registerAll(Kryo kryo) {
        // Primitive array types used for serialisation helpers
        kryo.register(byte[].class);
        kryo.register(int[].class);
        kryo.register(float[].class);
        kryo.register(String[].class);

        // Standard Java collection types
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);

        // Framework packets (alphabetical within each group)
        kryo.register(ChatMessage.class);
        kryo.register(ChatMessage.MessageType.class);
        kryo.register(ErrorPacket.class);
        kryo.register(HostMigration.class);
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(Ping.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        kryo.register(PlayerList.class);
        kryo.register(PlayerList.PlayerEntry.class);
        kryo.register(PlayerPosition.class);
        kryo.register(Pong.class);
    }
}
