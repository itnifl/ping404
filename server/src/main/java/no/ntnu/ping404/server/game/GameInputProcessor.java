package no.ntnu.ping404.server.game;

import no.ntnu.ping404.model.GameState;
import no.ntnu.ping404.model.GameState.Phase;
import no.ntnu.ping404.model.Player;
import no.ntnu.ping404.server.GameRoom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Drains the {@link InputQueue} each tick and applies paddle moves and pause/resume requests to the game state.
 */
public class GameInputProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GameInputProcessor.class);

    private final InputQueue inputQueue;
    private final GameRoom room;

    GameInputProcessor(InputQueue inputQueue, GameRoom room) {
        this.inputQueue = inputQueue;
        this.room = room;
    }

    /**
     * Drains all queued input events and applies them to the game state.
     */
    void process() {
        List<InputEvent> events = inputQueue.drainAll();
        for (InputEvent event : events) {
            switch (event.getType()) {
                case PADDLE_MOVE -> handlePaddleMove(event);
                case PAUSE_REQUEST -> handlePauseRequest(event);
                case RESUME_REQUEST -> handleResumeRequest(event);
            }
        }
    }

    private void handlePaddleMove(InputEvent event) {
        GameState gameState = room.getGameState();
        Player player = gameState.getPlayer(event.getConnectionId());
        if (player == null) return;

        player.setX(event.getX());
        player.setY(event.getY());
    }

    private void handlePauseRequest(InputEvent event) {
        if (room.getPhase() == Phase.PLAYING) {
            room.setPhase(Phase.PAUSED);
            logger.info("Room '{}' paused via game loop (connection {})", room.getRoomId(), event.getConnectionId());
        }
    }

    private void handleResumeRequest(InputEvent event) {
        if (room.getPhase() == Phase.PAUSED) {
            room.setPhase(Phase.PLAYING);
            logger.info("Room '{}' resumed via game loop (connection {})", room.getRoomId(), event.getConnectionId());
        }
    }
}
