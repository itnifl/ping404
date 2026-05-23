package no.ntnu.ping404.server.game;

import no.ntnu.ping404.server.GameRoom;

/**
 * Delegate for dispatching input events to a room's game loop.
 * Replaces direct GameServer dependency in handlers to avoid circular coupling.
 * Part of the Producer-Consumer pattern (issue #14).
 */
@FunctionalInterface
public interface InputEventDispatcher {
    void dispatch(GameRoom room, InputEvent event);
}
