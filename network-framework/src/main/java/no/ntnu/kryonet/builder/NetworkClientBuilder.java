package no.ntnu.kryonet.builder;

import no.ntnu.kryonet.core.INetworkClient;
import no.ntnu.kryonet.dispatch.client.ClientPacketDispatcher;
import no.ntnu.kryonet.dispatch.client.ClientPacketHandler;
import no.ntnu.kryonet.dispatch.client.ThreadDispatcher;
import no.ntnu.kryonet.internal.NetworkKryoClient;
import no.ntnu.kryonet.observer.NetworkListener;
import no.ntnu.kryonet.registry.FrameworkPacketRegistry;
import no.ntnu.kryonet.registry.UserPacketRegistry;

import java.util.ArrayList;
import java.util.List;

public final class NetworkClientBuilder {

    private boolean frameworkPackets;
    private final UserPacketRegistry userRegistry = new UserPacketRegistry();
    private final List<HandlerRegistration<?>> handlers = new ArrayList<>();
    private final List<NetworkListener> listeners = new ArrayList<>();
    private ThreadDispatcher threadDispatcher = ThreadDispatcher.DIRECT;

    NetworkClientBuilder() {}

    public NetworkClientBuilder withFrameworkPackets() {
        frameworkPackets = true;
        return this;
    }

    public NetworkClientBuilder withPacket(Class<?> packetClass) {
        userRegistry.register(packetClass);
        return this;
    }

    public NetworkClientBuilder withThreadDispatcher(ThreadDispatcher dispatcher) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("dispatcher must not be null");
        }
        threadDispatcher = dispatcher;
        return this;
    }

    public <T> NetworkClientBuilder onPacket(Class<T> packetClass, ClientPacketHandler<T> handler) {
        handlers.add(new HandlerRegistration<>(packetClass, handler));
        return this;
    }

    public NetworkClientBuilder addListener(NetworkListener listener) {
        listeners.add(listener);
        return this;
    }

    public INetworkClient build() {
        NetworkKryoClient networkClient = new NetworkKryoClient(kryo -> {
            if (frameworkPackets) {
                FrameworkPacketRegistry.registerAll(kryo);
            }
            userRegistry.applyTo(kryo);
        });

        ClientPacketDispatcher dispatcher = new ClientPacketDispatcher(threadDispatcher);
        for (HandlerRegistration<?> registration : handlers) {
            registration.applyTo(dispatcher);
        }

        networkClient.addListener(new NetworkListener.Adapter() {
            @Override
            public void onReceived(Object packet) {
                dispatcher.dispatch(packet);
            }
        });

        for (NetworkListener listener : listeners) {
            networkClient.addListener(listener);
        }

        return networkClient;
    }

    private record HandlerRegistration<T>(Class<T> packetClass, ClientPacketHandler<T> handler) {
        @SuppressWarnings("unchecked")
        void applyTo(ClientPacketDispatcher dispatcher) {
            dispatcher.register(packetClass, (ClientPacketHandler<T>) handler);
        }
    }
}
