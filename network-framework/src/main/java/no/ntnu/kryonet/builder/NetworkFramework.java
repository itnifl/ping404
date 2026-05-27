package no.ntnu.kryonet.builder;

/**
 * Entry point for constructing framework-managed network sessions.
 *
 * <pre>{@code
 * // Server example
 * INetworkServer server = NetworkFramework.serverBuilder()
 *     .withFrameworkPackets()
 *     .withPacket(MyPacket.class)
 *     .withFrameworkHandler(Ping.class)
 *     .withFrameworkHandler(Pong.class)
 *     .withHandler(MyPacket.class, (conn, pkt) -> { ... })
 *     .build();
 *
 * // Client example
 * INetworkClient client = NetworkFramework.clientBuilder()
 *     .withFrameworkPackets()
 *     .withPacket(MyPacket.class)
 *     .onPacket(MyPacket.class, pkt -> { ... })
 *     .build();
 * }</pre>
 */
public final class NetworkFramework {

    private NetworkFramework() {}

    public static NetworkServerBuilder serverBuilder() { return new NetworkServerBuilder(); }
    public static NetworkClientBuilder clientBuilder() { return new NetworkClientBuilder(); }
}
