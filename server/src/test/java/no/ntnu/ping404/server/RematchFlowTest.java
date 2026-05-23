package no.ntnu.ping404.server;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.packets.GameStartEvent;
import no.ntnu.ping404.network.packets.RematchRequest;
import no.ntnu.ping404.network.packets.RematchStart;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.server.handler.RematchHandlerCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rematch flow (Issue #60):
 * both players send RematchRequest, server resets and broadcasts RematchStart.
 */
class RematchFlowTest {

    private static final String ROOM_ID = "room-1";
    private static final int HOST_ID = 1;
    private static final int PLAYER_1_ID = 1;
    private static final int PLAYER_2_ID = 2;

    private GameRoom room;
    private RecordingServerConnector connector;
    private Map<Integer, GameRoom> playerRooms;
    private RematchHandlerCommand handler;

    private static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector() {
            super(new NetworkKryoServer());
        }

        @Override
        public void send(int connectionId, Object packet) {
            sentPackets.add(new SentPacket(connectionId, packet));
        }

        <T> List<T> getAllPacketsOfType(Class<T> type) {
            return sentPackets.stream()
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        <T> List<T> getPacketsSentToOfType(int connectionId, Class<T> type) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        private record SentPacket(int connectionId, Object packet) {}
    }

    @BeforeEach
    void setUp() {
        room = new GameRoom(ROOM_ID, HOST_ID);
        connector = new RecordingServerConnector();
        playerRooms = new ConcurrentHashMap<>();

        PlayerConnection conn1 = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID);
        PlayerConnection conn2 = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID);
        room.addPlayer(conn1, new no.ntnu.ping404.model.Player(PLAYER_1_ID, "Alice"));
        room.addPlayer(conn2, new no.ntnu.ping404.model.Player(PLAYER_2_ID, "Bob"));
        playerRooms.put(PLAYER_1_ID, room);
        playerRooms.put(PLAYER_2_ID, room);

        room.setPhase(GameState.Phase.PLAYING);
        room.setPhase(GameState.Phase.FINISHED);

        handler = new RematchHandlerCommand(connector, playerRooms, null);
    }

    @Test
    @Tag("FR1.5")
    void firstPlayerRematchIntentIsRecordedButDoesNotStartMatch() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());

        assertEquals(1, room.getRematchIntentCount());
        assertEquals(GameState.Phase.FINISHED, room.getPhase());
        assertTrue(connector.getAllPacketsOfType(RematchStart.class).isEmpty());
    }

    @Test
    @Tag("FR1.5")
    void secondPlayerRematchTriggersBroadcastAndResets() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID), new RematchRequest());

        List<RematchStart> broadcasts = connector.getAllPacketsOfType(RematchStart.class);
        assertEquals(2, broadcasts.size(), "RematchStart must be sent to both players");
    }

    @Test
    @Tag("FR1.5")
    void rematchRequestIgnoredWhenNotInFinishedPhase() {
        room.setPhase(GameState.Phase.WAITING);

        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());

        assertEquals(0, room.getRematchIntentCount());
        assertTrue(connector.getAllPacketsOfType(RematchStart.class).isEmpty());
    }

    @Test
    @Tag("FR1.5")
    void rematchRequestIgnoredForUnknownPlayer() {
        int unknownId = 999;

        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(unknownId), new RematchRequest());

        assertEquals(0, room.getRematchIntentCount());
        assertTrue(connector.getAllPacketsOfType(RematchStart.class).isEmpty());
    }

    @Test
    @Tag("FR1.5")
    void afterRematchRoomPhaseIsPlaying() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID), new RematchRequest());

        assertEquals(GameState.Phase.PLAYING, room.getPhase());
    }

    @Test
    @Tag("FR1.5")
    void afterRematchScoresAreReset() {
        room.getGameState().getScore().setPlayer1Score(3);
        room.getGameState().getScore().setPlayer2Score(5);

        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID), new RematchRequest());

        assertEquals(0, room.getGameState().getScore().getPlayer1Score());
        assertEquals(0, room.getGameState().getScore().getPlayer2Score());
    }

    @Test
    @Tag("FR1.5")
    void rematchStartPacketIsSentToBothPlayers() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID), new RematchRequest());

        assertFalse(connector.getPacketsSentToOfType(PLAYER_1_ID, RematchStart.class).isEmpty(),
                "Player 1 must receive RematchStart");
        assertFalse(connector.getPacketsSentToOfType(PLAYER_2_ID, RematchStart.class).isEmpty(),
                "Player 2 must receive RematchStart");
    }

    @Test
    @Tag("FR1.5")
    void samePlayerCannotVoteTwice() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());

        assertEquals(1, room.getRematchIntentCount(), "Duplicate vote must not be counted twice");
        assertEquals(GameState.Phase.FINISHED, room.getPhase(), "Rematch must not start from a single player voting twice");
    }

    @Test
    @Tag("FR1.5")
    void afterRematchGameStartEventSentToBothPlayers() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID), new RematchRequest());

        assertFalse(connector.getPacketsSentToOfType(PLAYER_1_ID, GameStartEvent.class).isEmpty(),
                "Player 1 must receive GameStartEvent");
        assertFalse(connector.getPacketsSentToOfType(PLAYER_2_ID, GameStartEvent.class).isEmpty(),
                "Player 2 must receive GameStartEvent");
    }

    @Test
    @Tag("FR1.5")
    void gameStartEventContainsCorrectSlotAssignments() {
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_1_ID), new RematchRequest());
        handler.handle(new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(PLAYER_2_ID), new RematchRequest());

        List<GameStartEvent> eventsForP1 = connector.getPacketsSentToOfType(PLAYER_1_ID, GameStartEvent.class);
        List<GameStartEvent> eventsForP2 = connector.getPacketsSentToOfType(PLAYER_2_ID, GameStartEvent.class);

        assertEquals(1, eventsForP1.get(0).playerSlot, "Player 1 (host) should be slot 1");
        assertEquals(2, eventsForP2.get(0).playerSlot, "Player 2 should be slot 2");
    }
}
