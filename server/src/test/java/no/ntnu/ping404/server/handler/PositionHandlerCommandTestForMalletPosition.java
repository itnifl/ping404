package no.ntnu.ping404.server.handler;
import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.network.NetworkKryoServer;
import no.ntnu.ping404.network.INetworkServer.PlayerConnection;
import no.ntnu.ping404.network.INetworkServer;
import no.ntnu.ping404.network.ServerConnector;
import no.ntnu.ping404.network.packets.PlayerPosition;
import no.ntnu.ping404.server.GameRoom;
import no.ntnu.ping404.utils.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Issue #25: Server-side validation of PlayerPosition.
 * 
 * Requirements:
 * - FR2.2: Mallet position must be validated before state update
 * - P1: Only validated positions should be broadcast
 * - TC6: Server applies validated input to authoritative state
 * 
 * Validation rules:
 * - Y must be inside board height (0 to BOARD_HEIGHT)
 * - X must be inside player's half only (cannot cross center line)
 * - Mallet radius must be included in boundary checks
 * 
 * Game layout (air hockey style):
 * - Player 1 is on LEFT half (X: 0 to CENTER_X)
 * - Player 2 is on RIGHT half (X: CENTER_X to BOARD_WIDTH)
 * - Center line is VERTICAL at X = CENTER_X = 400
 */
class PositionHandlerCommandTestForMalletPosition {

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    record SentPacket(int connectionId, Object packet) {}

    static class RecordingNetworkServer extends NetworkKryoServer {
        private final Map<Integer, INetworkServer.PlayerConnection> connections = new ConcurrentHashMap<>();

        public void registerConnection(INetworkServer.PlayerConnection conn) {
            connections.put(conn.getId(), conn);
        }

        @Override
        public void forEachConnection(java.util.function.BiConsumer<Integer, INetworkServer.PlayerConnection> action) {
            connections.forEach(action);
        }
    }

    static class RecordingServerConnector extends ServerConnector {
        private final List<SentPacket> sentPackets = new ArrayList<>();

        RecordingServerConnector(NetworkKryoServer networkServer) {
            super(networkServer);
        }

        @Override
        public void send(int connectionId, Object packet) {
            sentPackets.add(new SentPacket(connectionId, packet));
        }

        public List<SentPacket> getSentPackets() {
            return sentPackets;
        }

        public List<PlayerPosition> getPositionsSentTo(int connectionId) {
            return sentPackets.stream()
                    .filter(p -> p.connectionId == connectionId)
                    .map(SentPacket::packet)
                    .filter(PlayerPosition.class::isInstance)
                    .map(PlayerPosition.class::cast)
                    .toList();
        }

        public void clearPackets() {
            sentPackets.clear();
        }
    }

    /* 
        Constants for tests (derived from Constants.java)
    */

    private static final float BOARD_WIDTH = Constants.DEFAULT_FIELD_WIDTH;   // 800
    private static final float BOARD_HEIGHT = Constants.DEFAULT_FIELD_HEIGHT; // 480
    private static final float CENTER_X = BOARD_WIDTH / 2;                    // 400 (vertical center line)
    private static final float MALLET_RADIUS = Constants.PADDLE_WIDTH / 2;    // 7.5
    private static final float FLOAT_TOLERANCE = 0.01f;

    // Player 1 is on LEFT half (X: 0 to CENTER_X)
    // Player 2 is on RIGHT half (X: CENTER_X to BOARD_WIDTH)

    private static final int PLAYER_1_ID = 1;
    private static final int PLAYER_2_ID = 2;
    private static final int ORPHAN_PLAYER_ID = 999;
    private static final String ROOM_ID = "room-1";
    private static final String PLAYER_1_NAME = "Player1";
    private static final String PLAYER_2_NAME = "Player2";

    /** Amount past a boundary used to create clearly out-of-bounds positions in tests. */
    private static final float OOB_OFFSET = 50f;

    /* 
        Fixtures
     */

    private RecordingNetworkServer networkServer;
    private RecordingServerConnector connector;
    private Map<Integer, GameRoom> playerRooms;
    private PositionHandlerCommand handler;

    @BeforeEach
    void setUp() {
        networkServer = new RecordingNetworkServer();
        connector = new RecordingServerConnector(networkServer);
        playerRooms = new ConcurrentHashMap<>();
        handler = new PositionHandlerCommand(connector, playerRooms, null, null);
    }

    private PlayerConnection createConnection(int id) {
        PlayerConnection conn = new no.ntnu.ping404.network.NetworkKryoServer.KryoPlayerConnection(id);
        networkServer.registerConnection(conn);
        return conn;
    }

    private GameRoom createRoomWithTwoPlayers(int player1Id, int player2Id) {
        GameRoom room = new GameRoom(ROOM_ID, player1Id);
        
        PlayerConnection conn1 = createConnection(player1Id);
        PlayerConnection conn2 = createConnection(player2Id);
        
        Player p1 = new Player(player1Id, PLAYER_1_NAME);
        Player p2 = new Player(player2Id, PLAYER_2_NAME);
        
        room.addPlayer(conn1, p1);
        room.addPlayer(conn2, p2);
        
        playerRooms.put(player1Id, room);
        playerRooms.put(player2Id, room);

        room.setPhase(GameState.Phase.PLAYING);
        return room;
    }

    @Nested
    @DisplayName("Valid Position Handling")
    class ValidPositionHandling {

        @Test
        @Tag("TC6")
        @DisplayName("Valid position within player's half is applied to state")
        void validPositionWithinPlayerHalfIsAppliedToState() {
            // TC6: Server applies validated input to authoritative state.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Player 1 moves to valid position in left half (X < CENTER_X)
            float validX = CENTER_X / 2;     // 200 (well within left half)
            float validY = BOARD_HEIGHT / 2; // 240 (center Y, valid for both)

            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, validX, validY);
            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertEquals(validX, player1.getX(), FLOAT_TOLERANCE, "X should be updated");
            assertEquals(validY, player1.getY(), FLOAT_TOLERANCE, "Y should be updated");
        }

        @Test
        @Tag("TC6")
        @Tag("P1")
        @DisplayName("Valid position is broadcast to opponent")
        void validPositionIsBroadcastToOpponent() {
            // P1: Valid positions are sent to opponent.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float validX = CENTER_X / 2;     // 200 (player 1's left half)
            float validY = BOARD_HEIGHT / 2; // 240

            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, validX, validY);
            handler.handle(conn1, position);

            List<PlayerPosition> sentToPlayer2 = connector.getPositionsSentTo(PLAYER_2_ID);
            assertEquals(1, sentToPlayer2.size(), "Opponent should receive position update");
            assertEquals(validX, sentToPlayer2.get(0).x, FLOAT_TOLERANCE);
            assertEquals(validY, sentToPlayer2.get(0).y, FLOAT_TOLERANCE);
        }

        @Test
        @Tag("TC6")
        @DisplayName("Position is not sent back to sender")
        void positionIsNotSentBackToSender() {
            // TC6: Position is relayed to opponent only, not echoed back.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float validX = MALLET_RADIUS + 1;
            float validY = MALLET_RADIUS + 1;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, validX, validY);
            handler.handle(conn1, position);

            List<PlayerPosition> sentToPlayer1 = connector.getPositionsSentTo(PLAYER_1_ID);
            assertTrue(sentToPlayer1.isEmpty(), "Sender should not receive their own position");
        }

        @Test
        @Tag("TC6")
        @DisplayName("Player 2 valid position within right half is applied to state")
        void player2ValidPositionWithinRightHalfIsAppliedToState() {
            // TC6: Server applies validated input to authoritative state (Player 2).
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float validX = CENTER_X + (BOARD_WIDTH - CENTER_X) / 2; // 600 (center of right half)
            float validY = BOARD_HEIGHT / 2;

            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, validX, validY);
            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertEquals(validX, player2.getX(), FLOAT_TOLERANCE, "X should be updated for Player 2");
            assertEquals(validY, player2.getY(), FLOAT_TOLERANCE, "Y should be updated for Player 2");
        }
    }

    @Nested
    @DisplayName("Center Line Validation")
    class CenterLineValidation {

        @Test
        @Tag("FR2.2")
        @DisplayName("Player 1 cannot cross center line (X > CENTER_X)")
        void player1CannotCrossCenterLine() {
            // FR2.2: Player 1 is on left half; X must be <= CENTER_X - MALLET_RADIUS.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(GameState.Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Player 1 attempts to move past center line into right half
            float invalidX = CENTER_X + MALLET_RADIUS; // Clearly in right half
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, invalidX, BOARD_HEIGHT / 2);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);

            handler.handle(conn1, position);

            // Position should be rejected or clamped
            assertTrue(player1.getX() <= CENTER_X, 
                "Player 1 X should not exceed center line");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Player 2 cannot cross center line (X < CENTER_X)")
        void player2CannotCrossCenterLine() {
            // FR2.2: Player 2 is on right half; X must be >= CENTER_X + MALLET_RADIUS.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(GameState.Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            // Player 2 attempts to move past center line into left half
            float invalidX = CENTER_X - MALLET_RADIUS; // Clearly in left half
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, invalidX, BOARD_HEIGHT / 2);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);

            handler.handle(conn2, position);

            // Position should be rejected or clamped
            assertTrue(player2.getX() >= CENTER_X,
                "Player 2 X should not go left of center line");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Mallet touching center line is rejected (radius check)")
        void malletTouchingCenterLineIsRejected() {
            // FR2.2: Include mallet radius in boundary checks.
            // Player 1's center at (CENTER_X - MALLET_RADIUS + 1) means edge crosses line.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float edgeCrossesCenter = CENTER_X - MALLET_RADIUS + 1; // Edge would cross
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, edgeCrossesCenter, BOARD_HEIGHT / 2);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertTrue(player1.getX() <= CENTER_X - MALLET_RADIUS,
                "Mallet edge should not cross center line");
        }
        @Test
        @Tag("FR2.2")
        @DisplayName("Player 2 center line crossing is clamped to exact boundary")
        void player2CenterLineCrossingIsClampedToExactBoundary() {
            // FR2.2: Player 2 crossing center line should be clamped.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float invalidX = CENTER_X - MALLET_RADIUS; // Clearly in left half
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, invalidX, BOARD_HEIGHT / 2);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            float minAllowedX = CENTER_X + MALLET_RADIUS;
            assertEquals(minAllowedX, player2.getX(), FLOAT_TOLERANCE,
                "Player 2 X should be clamped to center line + mallet radius");
        }
    }

    // ------------------------------------------------------------------
    // FR2.2 â€” Board boundary violations
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Board Boundary Validation")
    class BoardBoundaryValidation {

        @Test
        @Tag("FR2.2")
        @DisplayName("Position outside left edge (X < 0) is rejected/clamped")
        void positionOutsideLeftEdgeIsRejected() {
            // FR2.2: X must be inside board width.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float invalidX = -OOB_OFFSET; // Outside left edge
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, invalidX, BOARD_HEIGHT / 2);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertTrue(player1.getX() >= MALLET_RADIUS,
                "X should be clamped to left boundary (including radius)");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Position outside right edge (X > BOARD_WIDTH) is rejected/clamped")
        void positionOutsideRightEdgeIsRejected() {
            // FR2.2: X must be inside board width.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float invalidX = BOARD_WIDTH + OOB_OFFSET; // Outside right edge
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, invalidX, BOARD_HEIGHT / 2);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertTrue(player2.getX() <= BOARD_WIDTH - MALLET_RADIUS,
                "X should be clamped to right boundary (including radius)");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Position outside bottom edge (Y < 0) is rejected/clamped")
        void positionOutsideBottomEdgeIsRejected() {
            // FR2.2: Y must be inside board height.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float invalidY = -OOB_OFFSET; // Outside bottom edge
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, BOARD_WIDTH / 2, invalidY);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertTrue(player1.getY() >= MALLET_RADIUS,
                "Y should be clamped to bottom boundary (including radius)");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Position outside top edge (Y > BOARD_HEIGHT) is rejected/clamped")
        void positionOutsideTopEdgeIsRejected() {
            // FR2.2: Y must be inside board height.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float invalidY = BOARD_HEIGHT + OOB_OFFSET; // Outside top edge
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, BOARD_WIDTH / 2, invalidY);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertTrue(player2.getY() <= BOARD_HEIGHT - MALLET_RADIUS,
                "Y should be clamped to top boundary (including radius)");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Mallet edge touching side wall is clamped (radius check)")
        void malletEdgeTouchingSideWallIsClamped() {
            // FR2.2: Include mallet radius in boundary checks.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Mallet center at (MALLET_RADIUS - 1) means left edge goes past wall
            float edgePastWall = MALLET_RADIUS - 1;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, edgePastWall, BOARD_HEIGHT / 2);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertTrue(player1.getX() >= MALLET_RADIUS,
                "Mallet edge should not go past left wall");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Player 2 mallet edge touching right wall is clamped")
        void player2MalletEdgeTouchingRightWallIsClamped() {
            // FR2.2: Include mallet radius in boundary checks for right wall.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            // Mallet center close to right wall â€” edge would cross
            float edgePastWall = BOARD_WIDTH - MALLET_RADIUS + 1;
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, edgePastWall, BOARD_HEIGHT / 2);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertTrue(player2.getX() <= BOARD_WIDTH - MALLET_RADIUS,
                "Mallet edge should not go past right wall");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Both X and Y simultaneously out-of-bounds are both clamped")
        void bothXAndYSimultaneouslyOutOfBoundsAreBothClamped() {
            // FR2.2: Both coordinates should be clamped independently.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Both coordinates wildly out of bounds
            float invalidX = -OOB_OFFSET;
            float invalidY = BOARD_HEIGHT + OOB_OFFSET;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, invalidX, invalidY);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertEquals(MALLET_RADIUS, player1.getX(), FLOAT_TOLERANCE,
                "X should be clamped to left boundary");
            assertEquals(BOARD_HEIGHT - MALLET_RADIUS, player1.getY(), FLOAT_TOLERANCE,
                "Y should be clamped to top boundary");
        }
    }

    // ------------------------------------------------------------------
    // FR2.2 â€” Invalid position not broadcast
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid Position Broadcast Prevention")
    class InvalidPositionBroadcastPrevention {

        @Test
        @Tag("FR2.2")
        @Tag("P1")
        @DisplayName("Invalid position is not broadcast to opponent")
        void invalidPositionIsNotBroadcastToOpponent() {
            // P1: Only validated positions are sent to opponent.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Player 1 attempts invalid position (crossing center line into right half)
            float invalidX = CENTER_X + OOB_OFFSET;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, invalidX, BOARD_HEIGHT / 2);

            handler.handle(conn1, position);

            List<PlayerPosition> sentToPlayer2 = connector.getPositionsSentTo(PLAYER_2_ID);
            // Either nothing is sent, or if clamped, the clamped position is sent
            for (PlayerPosition sent : sentToPlayer2) {
                assertTrue(sent.x <= CENTER_X,
                    "Opponent should only receive validated positions");
            }
        }

        @Test
        @Tag("P1")
        @Tag("FR2.2")
        @DisplayName("Broadcast after clamping contains clamped values, not original")
        void broadcastAfterClampingContainsClampedValues() {
            // P1: Clamped values must be what the opponent sees.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Player 1 sends far out-of-bounds position
            float invalidX = CENTER_X + OOB_OFFSET; // Way past center line
            float invalidY = -OOB_OFFSET;           // Below board
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, invalidX, invalidY);

            handler.handle(conn1, position);

            float expectedX = CENTER_X - MALLET_RADIUS; // Clamped to center line
            float expectedY = MALLET_RADIUS;              // Clamped to bottom edge

            List<PlayerPosition> sentToPlayer2 = connector.getPositionsSentTo(PLAYER_2_ID);
            assertEquals(1, sentToPlayer2.size(), "Clamped position should still be broadcast");
            assertEquals(expectedX, sentToPlayer2.get(0).x, FLOAT_TOLERANCE, "Broadcast X should be the clamped value");
            assertEquals(expectedY, sentToPlayer2.get(0).y, FLOAT_TOLERANCE, "Broadcast Y should be the clamped value");
        }
    }

    // ------------------------------------------------------------------
    // Policy tests â€” Reject vs Clamp
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid Position Policy")
    class InvalidPositionPolicy {

        @Test
        @Tag("FR2.2")
        @DisplayName("Invalid position is clamped to valid boundaries")
        void invalidPositionIsClampedToValidBoundaries() {
            // Policy: Invalid positions are clamped to the nearest valid point.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Attempt invalid position (X past center line)
            PlayerPosition invalidPos = new PlayerPosition(PLAYER_1_ID, CENTER_X + OOB_OFFSET, BOARD_HEIGHT / 2);
            handler.handle(conn1, invalidPos);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            // Position should be clamped to center line boundary
            float maxAllowedX = CENTER_X - MALLET_RADIUS;
            assertEquals(maxAllowedX, player1.getX(), FLOAT_TOLERANCE, "X should be clamped to center line");
            assertEquals(BOARD_HEIGHT / 2, player1.getY(), FLOAT_TOLERANCE, "Y should remain valid");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Clamp policy: invalid position adjusted to nearest valid point")
        void clampPolicyAdjustsToNearestValidPoint() {
            // Policy: If clamp approach is chosen, position is snapped to boundary.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);

            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Attempt position past center line into right half
            float attemptedX = CENTER_X + OOB_OFFSET;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, attemptedX, BOARD_HEIGHT / 2);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            // If clamped, X should be at maximum allowed (CENTER_X - MALLET_RADIUS)
            float maxAllowedX = CENTER_X - MALLET_RADIUS;
            assertEquals(maxAllowedX, player1.getX(), FLOAT_TOLERANCE,
                "X should be clamped to center line boundary");
        }
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @Tag("TC6")
        @DisplayName("Position update for non-existent player is ignored")
        void positionUpdateForNonExistentPlayerIsIgnored() {
            // Edge case: Connection not in any room should be safely ignored.
            PlayerConnection orphanConn = createConnection(ORPHAN_PLAYER_ID);

            PlayerPosition position = new PlayerPosition(ORPHAN_PLAYER_ID, MALLET_RADIUS + 1, MALLET_RADIUS + 1);
            
            // Should not throw
            assertDoesNotThrow(() -> handler.handle(orphanConn, position));
            assertTrue(connector.getSentPackets().isEmpty());
        }

        @Test
        @Tag("TC6")
        @DisplayName("Position at exact center line boundary is valid for Player 1")
        void positionAtExactCenterLineBoundaryIsValidForPlayer1() {
            // Edge case: X exactly at (CENTER_X - MALLET_RADIUS) should be valid.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float boundaryX = CENTER_X - MALLET_RADIUS;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, boundaryX, BOARD_HEIGHT / 2);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            // This should be accepted (on the boundary is valid)
            assertEquals(boundaryX, player1.getX(), FLOAT_TOLERANCE,
                "Position exactly at boundary should be accepted");
        }

        @Test
        @Tag("TC6")
        @DisplayName("Position at exact center line boundary is valid for Player 2")
        void positionAtExactCenterLineBoundaryIsValidForPlayer2() {
            // Edge case: X exactly at (CENTER_X + MALLET_RADIUS) should be valid.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float boundaryX = CENTER_X + MALLET_RADIUS;
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, boundaryX, BOARD_HEIGHT / 2);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertEquals(boundaryX, player2.getX(), FLOAT_TOLERANCE,
                "Position exactly at center line boundary should be accepted for Player 2");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Position at exact corner is handled correctly")
        void positionAtExactCornerIsHandledCorrectly() {
            // Edge case: Position at corner of valid area.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);

            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            // Bottom-left corner of valid area for Player 1
            float cornerX = MALLET_RADIUS;
            float cornerY = MALLET_RADIUS;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, cornerX, cornerY);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertEquals(cornerX, player1.getX(), FLOAT_TOLERANCE);
            assertEquals(cornerY, player1.getY(), FLOAT_TOLERANCE);
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Player 1 top-right corner of valid area is accepted")
        void player1TopRightCornerOfValidAreaIsAccepted() {
            // Edge case: Top-right corner of Player 1's valid area.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn1 = room.getConnections().get(PLAYER_1_ID);

            float cornerX = CENTER_X - MALLET_RADIUS;
            float cornerY = BOARD_HEIGHT - MALLET_RADIUS;
            PlayerPosition position = new PlayerPosition(PLAYER_1_ID, cornerX, cornerY);

            handler.handle(conn1, position);

            Player player1 = room.getGameState().getPlayer(PLAYER_1_ID);
            assertEquals(cornerX, player1.getX(), FLOAT_TOLERANCE,
                "Top-right X corner should be accepted");
            assertEquals(cornerY, player1.getY(), FLOAT_TOLERANCE,
                "Top-right Y corner should be accepted");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Player 2 bottom-left corner of valid area is accepted")
        void player2BottomLeftCornerOfValidAreaIsAccepted() {
            // Edge case: Bottom-left corner of Player 2's valid area.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float cornerX = CENTER_X + MALLET_RADIUS;
            float cornerY = MALLET_RADIUS;
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, cornerX, cornerY);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertEquals(cornerX, player2.getX(), FLOAT_TOLERANCE,
                "Bottom-left X corner of Player 2 valid area should be accepted");
            assertEquals(cornerY, player2.getY(), FLOAT_TOLERANCE,
                "Bottom-left Y corner should be accepted");
        }

        @Test
        @Tag("FR2.2")
        @DisplayName("Player 2 top-right corner of valid area is accepted")
        void player2TopRightCornerOfValidAreaIsAccepted() {
            // Edge case: Top-right corner of Player 2's valid area.
            GameRoom room = createRoomWithTwoPlayers(PLAYER_1_ID, PLAYER_2_ID);
            room.setPhase(Phase.PLAYING);
            PlayerConnection conn2 = room.getConnections().get(PLAYER_2_ID);

            float cornerX = BOARD_WIDTH - MALLET_RADIUS;
            float cornerY = BOARD_HEIGHT - MALLET_RADIUS;
            PlayerPosition position = new PlayerPosition(PLAYER_2_ID, cornerX, cornerY);

            handler.handle(conn2, position);

            Player player2 = room.getGameState().getPlayer(PLAYER_2_ID);
            assertEquals(cornerX, player2.getX(), FLOAT_TOLERANCE,
                "Top-right X corner of Player 2 valid area should be accepted");
            assertEquals(cornerY, player2.getY(), FLOAT_TOLERANCE,
                "Top-right Y corner should be accepted");
        }
    }
}

