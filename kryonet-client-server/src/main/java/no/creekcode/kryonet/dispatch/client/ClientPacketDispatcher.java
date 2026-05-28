package no.creekcode.kryonet.dispatch.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes incoming packets to their registered {@link ClientPacketHandler} and dispatches
 * the callback via an injected {@link ThreadDispatcher}.
 *
 * <p>Thread safety: the handler map is a {@link ConcurrentHashMap}; registration may occur
 * concurrently with dispatch. By default dispatches run synchronously on the calling thread
 * ({@link ThreadDispatcher#DIRECT}). LibGDX callers should supply {@code Gdx.app::postRunnable}
 * so handlers execute on the render thread.</p>
 */
public class ClientPacketDispatcher {

    private final Map<Class<?>, ClientPacketHandler<?>> handlers = new ConcurrentHashMap<>();
    private final ThreadDispatcher threadDispatcher;

    /** Creates a dispatcher that executes handlers synchronously. */
    public ClientPacketDispatcher() {
        this(ThreadDispatcher.DIRECT);
    }

    public ClientPacketDispatcher(ThreadDispatcher threadDispatcher) {
        if (threadDispatcher == null) throw new IllegalArgumentException("threadDispatcher must not be null");
        this.threadDispatcher = threadDispatcher;
    }

    /** Registers a typed handler for the given packet class. Returns {@code this} for chaining. */
    public <T> ClientPacketDispatcher register(Class<T> packetClass, ClientPacketHandler<T> handler) {
        handlers.put(packetClass, handler);
        return this;
    }

    /** Dispatches the packet to its registered handler via the configured {@link ThreadDispatcher}. */
    @SuppressWarnings("unchecked")
    public void dispatch(Object packet) {
        if (packet == null) return;
        ClientPacketHandler<Object> handler = (ClientPacketHandler<Object>) handlers.get(packet.getClass());
        if (handler == null) return;
        threadDispatcher.dispatch(() -> handler.handle(packet));
    }

    public boolean hasHandler(Class<?> packetClass) {
        return handlers.containsKey(packetClass);
    }

    public void clear() {
        handlers.clear();
    }
}
