package no.ntnu.kryonet.handler;

import java.util.List;

/**
 * Resolves the set of connection IDs that share a room with a given connection.
 *
 * <p>Implement this in your game code and pass it to {@link ChatHandlerCommand}.
 * The framework does not define any concept of "rooms"; this callback lets
 * {@link ChatHandlerCommand} broadcast without importing game-specific types.</p>
 */
@FunctionalInterface
public interface RoomResolver {

    /**
     * Returns the connection IDs (including the sender) that should receive the
     * broadcast for {@code connectionId}. Return an empty list if the connection
     * is not in any room.
     */
    List<Integer> roomMatesOf(int connectionId);
}
