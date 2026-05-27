package no.ntnu.kryonet.builder;

import no.ntnu.kryonet.core.INetworkServer;
import no.ntnu.kryonet.core.ServerConnector;
import no.ntnu.kryonet.dispatch.server.PacketHandlerCommand;
import no.ntnu.kryonet.dispatch.server.ServerPacketRouter;
import no.ntnu.kryonet.handler.ChatHandlerCommand;
import no.ntnu.kryonet.handler.PingHandlerCommand;
import no.ntnu.kryonet.handler.PongHandlerCommand;
import no.ntnu.kryonet.handler.RoomResolver;
import no.ntnu.kryonet.handler.RttListener;
import no.ntnu.kryonet.internal.NetworkKryoServer;
import no.ntnu.kryonet.packets.ChatMessage;
import no.ntnu.kryonet.packets.Ping;
import no.ntnu.kryonet.packets.PlayerPosition;
import no.ntnu.kryonet.packets.Pong;
import no.ntnu.kryonet.registry.FrameworkPacketRegistry;
import no.ntnu.kryonet.registry.UserPacketRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NetworkServerBuilder {

    private boolean frameworkPackets;
    private final UserPacketRegistry userRegistry = new UserPacketRegistry();
    private final Set<Class<?>> unreliableTypes = new HashSet<>();
    private final List<PacketRegistration> handlers = new ArrayList<>();
    private final List<INetworkServer.ServerListener> extraListeners = new ArrayList<>();

    private boolean handlePing;
    private boolean handlePong;
    private RttListener rttListener;
    private boolean handleChat;
    private RoomResolver roomResolver;

    NetworkServerBuilder() {}

    public NetworkServerBuilder withFrameworkPackets() {
        frameworkPackets = true;
        unreliableTypes.add(Ping.class);
        unreliableTypes.add(Pong.class);
        unreliableTypes.add(PlayerPosition.class);
        return this;
    }

    public NetworkServerBuilder withPacket(Class<?> packetClass) {
        userRegistry.register(packetClass);
        return this;
    }

    public NetworkServerBuilder unreliable(Class<?>... classes) {
        for (Class<?> c : classes) {
            unreliableTypes.add(c);
        }
        return this;
    }

    public NetworkServerBuilder withFrameworkHandler(Class<?> packetClass) {
        if (packetClass == Ping.class) {
            handlePing = true;
        } else if (packetClass == Pong.class) {
            handlePong = true;
        } else if (packetClass == ChatMessage.class) {
            handleChat = true;
        } else {
            throw new IllegalArgumentException("No built-in handler for: " + packetClass.getName());
        }
        return this;
    }

    public NetworkServerBuilder withRttListener(RttListener listener) {
        rttListener = listener;
        return this;
    }

    public NetworkServerBuilder withRoomResolver(RoomResolver resolver) {
        roomResolver = resolver;
        return this;
    }

    public NetworkServerBuilder withHandler(Class<?> packetClass, PacketHandlerCommand command) {
        handlers.add(new PacketRegistration(packetClass, command));
        return this;
    }

    public NetworkServerBuilder addListener(INetworkServer.ServerListener listener) {
        extraListeners.add(listener);
        return this;
    }

    public INetworkServer build() {
        if ((handlePing || handlePong || handleChat) && !frameworkPackets) {
            throw new IllegalStateException("withFrameworkPackets() must be called before withFrameworkHandler()");
        }
        if (handleChat && roomResolver == null) {
            throw new IllegalStateException("withRoomResolver() is required when handling ChatMessage");
        }

        NetworkKryoServer networkServer = new NetworkKryoServer(kryo -> {
            if (frameworkPackets) {
                FrameworkPacketRegistry.registerAll(kryo);
            }
            userRegistry.applyTo(kryo);
        });

        ServerConnector connector = new ServerConnector(networkServer, new HashSet<>(unreliableTypes));
        ServerPacketRouter router = new ServerPacketRouter();

        if (handlePing) {
            router.register(Ping.class, new PingHandlerCommand(connector));
        }
        if (frameworkPackets || handlePong) {
            router.register(Pong.class, new PongHandlerCommand(rttListener));
        }
        if (handleChat) {
            router.register(ChatMessage.class, new ChatHandlerCommand(connector, roomResolver));
        }
        for (PacketRegistration registration : handlers) {
            router.register(registration.packetClass(), registration.command());
        }

        networkServer.addListener(new INetworkServer.ServerListenerAdapter() {
            @Override
            public void onReceived(INetworkServer.PlayerConnection connection, Object packet) {
                router.dispatch(connection, packet);
            }
        });

        for (INetworkServer.ServerListener listener : extraListeners) {
            networkServer.addListener(listener);
        }

        return networkServer;
    }

    private record PacketRegistration(Class<?> packetClass, PacketHandlerCommand command) {}
}
