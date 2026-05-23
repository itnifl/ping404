package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.Constants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the current state of a game match with validated state machine transitions.
 * Holds players, puck, score, and match phase with guards preventing invalid transitions.
 */
public class GameState {

    private static final Logger logger = LoggerFactory.getLogger(GameState.class);

    public enum Phase {
        WAITING,    // Waiting for players to join
        PLAYING,    // Match in progress
        PAUSED,     // Match paused
        FINISHED    // Match over
    }

    private final Map<Integer, Player> players;
    private Puck puck;
    private Score score;
    private Phase phase;
    private float fieldWidth;
    private float fieldHeight;

    public GameState() {
        this.players = new ConcurrentHashMap<>();
        this.puck = new Puck();
        this.score = new Score();
        this.phase = Phase.WAITING;
        this.fieldWidth = Constants.BOARD_WIDTH;
        this.fieldHeight = Constants.BOARD_HEIGHT;
    }

    public GameState(int winScore) {
        this.players = new ConcurrentHashMap<>();
        this.puck = new Puck();
        this.score = new Score(winScore);
        this.phase = Phase.WAITING;
        this.fieldWidth = Constants.BOARD_WIDTH;
        this.fieldHeight = Constants.BOARD_HEIGHT;
    }

    public GameState(float fieldWidth, float fieldHeight) {
        this();
        this.fieldWidth = fieldWidth;
        this.fieldHeight = fieldHeight;
    }

    public void addPlayer(Player player) {
        players.put(player.getId(), player);
    }

    public void removePlayer(int playerId) {
        players.remove(playerId);
    }

    public Player getPlayer(int playerId) {
        return players.get(playerId);
    }

    public Map<Integer, Player> getPlayers() { return players; }
    public int getPlayerCount() { return players.size(); }

    public Puck getPuck() { return puck; }
    public void setPuck(Puck puck) { this.puck = puck; }

    public Score getScore() { return score; }
    public void setScore(Score score) { this.score = score; }

    public Phase getPhase() { return phase; }

    /**
     * Direct phase setter for tests.
     *
     * @deprecated Use state machine methods instead
     */
    @Deprecated(forRemoval = false, since = "1.0")
    public void setPhase(Phase phase) {
        logger.warn("Direct setPhase() called with {}. Prefer state machine methods.", phase);
        this.phase = phase;
    }

    public float getFieldWidth() { return fieldWidth; }
    public float getFieldHeight() { return fieldHeight; }

    /**
     * Reset game state for a new round (keeps players, resets puck and score).
     * Transitions to WAITING phase.
     */
    public void resetForNewMatch() {
        puck.reset(fieldWidth / 2, fieldHeight / 2);
        score.reset();
        phase = Phase.WAITING;
        for (Player p : players.values()) {
            p.setScore(0);
            p.setReady(false);
        }
        logger.info("Match reset to WAITING phase");
    }

    public boolean startMatch() {
        if (phase != Phase.WAITING) {
            logger.warn("Cannot start match: phase is {}, expected WAITING", phase);
            return false;
        }
        if (players.size() < 2) {
            logger.warn("Cannot start match: only {} player(s) connected, need 2", players.size());
            return false;
        }
        phase = Phase.PLAYING;
        logger.info("Match transitioned to PLAYING phase ({} players)", players.size());
        return true;
    }

    /**
     * Begin playing helper.
     * Kept for compatibility, behaves as idempotent start.
     *
     * @return true if transition succeeded, false otherwise
     */
    public boolean beginPlaying() {
        if (phase == Phase.PLAYING) {
            return true;
        }
        if (phase != Phase.WAITING) {
            logger.warn("Cannot begin playing: phase is {}, expected WAITING", phase);
            return false;
        }
        return startMatch();
    }

    /**
     * Pause match: PLAYING --> PAUSED (FR4.1)
     * Only allowed when actively playing.
     *
     * @return true if transition succeeded, false otherwise
     */
    public boolean pauseMatch() {
        if (phase != Phase.PLAYING) {
            logger.warn("Cannot pause match: phase is {}, expected PLAYING", phase);
            return false;
        }
        phase = Phase.PAUSED;
        logger.info("Match transitioned to PAUSED phase");
        return true;
    }

    /**
     * Resume match: PAUSED --> PLAYING (FR4.2)
     * Only allowed when match is paused.
     *
     * @return true if transition succeeded, false otherwise
     */
    public boolean resumeMatch() {
        if (phase != Phase.PAUSED) {
            logger.warn("Cannot resume match: phase is {}, expected PAUSED", phase);
            return false;
        }
        phase = Phase.PLAYING;
        logger.info("Match transitioned to PLAYING phase (resumed)");
        return true;
    }

    /**
     * Finish match: PLAYING --> FINISHED (FR2.9, FR3.1)
     * Triggered when win condition is met (score reaches limit).
     * Idempotent: safe to call multiple times.
     *
     * @return true if transition succeeded, false otherwise
     */
    public boolean finishMatch() {
        // Idempotent: already finished
        if (phase == Phase.FINISHED) {
            logger.debug("finishMatch called but already FINISHED (idempotent)");
            return true;
        }
        
        if (phase != Phase.PLAYING) {
            logger.warn("Cannot finish match: phase is {}, expected PLAYING", phase);
            return false;
        }
        
        phase = Phase.FINISHED;
        logger.info("Match transitioned to FINISHED phase. Winner: Player {}, Score: {}-{}",
                    getScore().getWinner(),
                    getScore().getPlayer1Score(),
                    getScore().getPlayer2Score());
        return true;
    }
}