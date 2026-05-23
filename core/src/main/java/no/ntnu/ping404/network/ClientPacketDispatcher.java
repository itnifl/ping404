package no.ntnu.ping404.network;

import com.badlogic.gdx.Gdx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side packet dispatcher that maps authoritative server packets to UI-safe state updates.
 * 
 * <p>This dispatcher ensures thread safety by:</p>
 * <ul>
 *   <li>Accepting packets from any thread (network thread)</li>
 *   <li>Enqueueing updates for deterministic processing on the render thread</li>
 *   <li>Routing packets to type-specific handlers</li>
 * </ul>
 *
 * <p>Supports packets: GameStateSnapshot, GoalScored, GameOver, PauseEvent, ResumeEvent,
 * PlayerJoined, PlayerLeft (FR1.5, FR2.8, FR3.1, FR3.2, FR4.1, FR4.2).</p>
 *
 * @see ClientPacketHandler
 */
public class ClientPacketDispatcher {

    private final Map<Class<?>, ClientPacketHandler<?>> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a typed packet handler.
     *
     * @param <T> the packet type
     * @param packetClass the class of packets to handle
     * @param handler the handler to invoke for this packet type
     * @return this dispatcher for chaining
     */
    public <T> ClientPacketDispatcher register(Class<T> packetClass, ClientPacketHandler<T> handler) {
        handlers.put(packetClass, handler);
        return this;
    }

    /**
     * Dispatches a packet to its registered handler.
     * The handler is invoked on the render thread via {@code Gdx.app.postRunnable()}
     * when available, or executed synchronously otherwise.
     *
     * @param packet the packet received from the server
     */
    @SuppressWarnings("unchecked")
    public void dispatch(Object packet) {
        if (packet == null) return;

        ClientPacketHandler<Object> handler = (ClientPacketHandler<Object>) handlers.get(packet.getClass());
        if (handler == null) return;

        Runnable update = () -> handler.handle(packet);

        if (Gdx.app != null) {
            Gdx.app.postRunnable(update);
        } else {
            update.run();
        }
    }

    /**
     * Checks if a handler is registered for the given packet type.
     *
     * @param packetClass the packet class to check
     * @return true if a handler is registered
     */
    public boolean hasHandler(Class<?> packetClass) {
        return handlers.containsKey(packetClass);
    }

    /**
     * Clears all registered handlers.
     */
    public void clear() {
        handlers.clear();
    }
}
