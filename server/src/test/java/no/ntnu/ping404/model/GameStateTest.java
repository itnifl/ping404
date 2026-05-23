package no.ntnu.ping404.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    private static final int CONNECTION_ID_1 = 1;
    private static final int CONNECTION_ID_2 = 2;
    private static final String PLAYER_1_NAME = "Player1";
    private static final String PLAYER_2_NAME = "Player2";
    private static final float POSITION_X = 100f;
    private static final float POSITION_Y = 200f;
    private static final float CENTER_X = 400f;
    private static final float CENTER_Y = 240f;
    private static final int WIN_SCORE_5 = 5;
    private static final int PLAYER_1_SLOT = 1;
    private static final int PLAYER_2_SLOT = 2;
    private static final int NO_WINNER = 0;

    @Test
    @Tag("FR1.5")
    void gameStateInitialPhaseIsWaiting() {
        GameState gameState = new GameState();
        assertEquals(GameState.Phase.WAITING, gameState.getPhase(), "Initial phase should be WAITING");
    }

    @Test
    @Tag("FR1.5")
    void playerIsRetrievableAfterBeingAdded() {
        GameState gameState = new GameState();
        Player player = new Player(CONNECTION_ID_1, PLAYER_1_NAME);
        gameState.addPlayer(player);
        assertEquals(player, gameState.getPlayer(CONNECTION_ID_1), "Player should be retrievable after being added");
    }

    @Test
    @Tag("FR4.3")
    void playerIsAbsentAfterBeingRemoved() {
        GameState gameState = new GameState();
        Player player = new Player(CONNECTION_ID_1, PLAYER_1_NAME);
        gameState.addPlayer(player);
        gameState.removePlayer(CONNECTION_ID_1);
        assertNull(gameState.getPlayer(CONNECTION_ID_1), "Player should be absent after being removed");
    }

    @Test
    @Tag("FR1.5")
    void playerCountReflectsCurrentPlayers() {
        GameState gameState = new GameState();
        Player player1 = new Player(CONNECTION_ID_1, PLAYER_1_NAME);
        Player player2 = new Player(CONNECTION_ID_2, PLAYER_2_NAME);
        gameState.addPlayer(player1);
        gameState.addPlayer(player2);
        assertEquals(2, gameState.getPlayerCount(), "Player count should reflect current players");
        gameState.removePlayer(CONNECTION_ID_1);
        assertEquals(1, gameState.getPlayerCount(), "Player count should update after removal");
    }

    @Test
    @Tag("FR2.10")
    void resetForNewMatchResetsScoreAndPuck() {
        GameState gameState = new GameState();

        gameState.getScore().incrementScore(PLAYER_1_SLOT);
        gameState.getPuck().setPosition(POSITION_X, POSITION_Y);
        gameState.resetForNewMatch();
        assertEquals(0, gameState.getScore().getPlayer1Score(), "Score should reset to 0");
        assertEquals(CENTER_X, gameState.getPuck().getX(), "Puck should reset to center X");
        assertEquals(CENTER_Y, gameState.getPuck().getY(), "Puck should reset to center Y");
    }

    @Test
    @Tag("FR2.10")
    void resetForNewMatchSetsPhaseToWaiting() {
        GameState gameState = buildPlayingState();
        gameState.resetForNewMatch();
        assertEquals(GameState.Phase.WAITING, gameState.getPhase(), "Phase should reset to WAITING after resetForNewMatch");
    }

    @Test
    @Tag("TC5")
    void playerMapUsesConcurrentHashMapForThreadSafety() {
        GameState gameState = new GameState();
        assertTrue(gameState.getPlayers() instanceof java.util.concurrent.ConcurrentHashMap, "Players map should use ConcurrentHashMap for thread safety");
    }

    @Test
    @Tag("TC6")
    void positionUpdateFromClientIsAppliedToServerState() {
        GameState gameState = new GameState();
        Player player = new Player(CONNECTION_ID_1, PLAYER_1_NAME);
        gameState.addPlayer(player);
        player.setX(POSITION_X);
        player.setY(POSITION_Y);
        assertEquals(POSITION_X, gameState.getPlayer(CONNECTION_ID_1).getX(), "Player X position should update");
        assertEquals(POSITION_Y, gameState.getPlayer(CONNECTION_ID_1).getY(), "Player Y position should update");
    }

    @Test
    @Tag("FR1.4")
    void startMatchTransitionsPhaseToPlaying() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(CONNECTION_ID_1, PLAYER_1_NAME));
        gameState.addPlayer(new Player(CONNECTION_ID_2, PLAYER_2_NAME));
        gameState.startMatch();
        gameState.beginPlaying();
        assertEquals(GameState.Phase.PLAYING, gameState.getPhase(), "Phase should transition to PLAYING when match starts");
    }

    @Test
    @Tag("FR4.1")
    void pauseMatchTransitionsPhaseToPaused() {
        GameState gameState = buildPlayingState();
        gameState.pauseMatch();
        assertEquals(GameState.Phase.PAUSED, gameState.getPhase(), "Phase should transition to PAUSED when match is paused");
    }

    @Test
    @Tag("FR4.2")
    void resumeMatchTransitionsPhaseToPlaying() {
        GameState gameState = buildPausedState();
        gameState.resumeMatch();
        assertEquals(GameState.Phase.PLAYING, gameState.getPhase(), "Phase should transition to PLAYING when match is resumed");
    }

    @Test
    @Tag("FR3.1")
    void finishMatchTransitionsPhaseToFinished() {
        GameState gameState = buildPlayingState();
        gameState.finishMatch();
        assertEquals(GameState.Phase.FINISHED, gameState.getPhase(), "Phase should transition to FINISHED when match ends");
    }

    @Test
    @Tag("FR3.1")
    void finishMatchDoesNothingWhenNotPlaying() {
        GameState gameState = new GameState();
        gameState.finishMatch();
        assertEquals(GameState.Phase.WAITING, gameState.getPhase(), "finishMatch should do nothing when not in PLAYING phase");
    }

    @Test
    @Tag("FR1.5")
    void startMatchRequiresTwoPlayers() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(CONNECTION_ID_1, PLAYER_1_NAME));
        gameState.startMatch();
        assertEquals(GameState.Phase.WAITING, gameState.getPhase(), "Phase should remain WAITING if only one player is in the game");
    }

    // ============ ADD BELOW: State machine return value & guard tests ============

    @Test
    @Tag("FR1.4")
    void startMatchReturnsTrueWith2Players() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(1, "Player1"));
        gameState.addPlayer(new Player(2, "Player2"));

        assertTrue(gameState.startMatch(), "startMatch should return true with 2 players");
        assertEquals(GameState.Phase.PLAYING, gameState.getPhase());
    }

    @Test
    @Tag("FR1.4")
    void startMatchReturnsFalseWhenNotWaiting() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(1, "Player1"));
        gameState.addPlayer(new Player(2, "Player2"));
        gameState.startMatch(); // WAITING -> PLAYING

        assertFalse(gameState.startMatch(), "startMatch should fail when already in PLAYING");
        assertEquals(GameState.Phase.PLAYING, gameState.getPhase());
    }

    @Test
    @Tag("FR1.5")
    void beginPlayingIsIdempotentWhenAlreadyPlaying() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(1, "Player1"));
        gameState.addPlayer(new Player(2, "Player2"));
        gameState.startMatch();

        assertTrue(gameState.beginPlaying(), "beginPlaying should return true when already PLAYING");
        assertEquals(GameState.Phase.PLAYING, gameState.getPhase());
    }

    @Test
    @Tag("FR1.5")
    void beginPlayingFailsWhenNotCountdown() {
        GameState gameState = new GameState();

        assertFalse(gameState.beginPlaying(), "beginPlaying should fail from WAITING");
        assertEquals(GameState.Phase.WAITING, gameState.getPhase());
    }

    @Test
    @Tag("FR4.1")
    void pauseMatchReturnsFalseWhenNotPlaying() {
        GameState gameState = new GameState();

        assertFalse(gameState.pauseMatch(), "pauseMatch should fail from WAITING");
        assertEquals(GameState.Phase.WAITING, gameState.getPhase());
    }

    @Test
    @Tag("FR4.2")
    void resumeMatchReturnsFalseWhenNotPaused() {
        GameState gameState = new GameState();

        assertFalse(gameState.resumeMatch(), "resumeMatch should fail from WAITING");
        assertEquals(GameState.Phase.WAITING, gameState.getPhase());
    }

    @Test
    @Tag("FR2.9")
    @Tag("FR3.1")
    void finishMatchSucceedsFromPlaying() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(1, "Player1"));
        gameState.addPlayer(new Player(2, "Player2"));
        gameState.startMatch(); // WAITING -> PLAYING

        assertTrue(gameState.finishMatch(), "finishMatch should succeed from PLAYING");
        assertEquals(GameState.Phase.FINISHED, gameState.getPhase());
    }

    @Test
    @Tag("FR3.1")
    void finishMatchIsIdempotent() {
        GameState gameState = buildPlayingState();
        gameState.finishMatch();

        assertTrue(gameState.finishMatch(), "finishMatch should return true when already FINISHED");
        assertEquals(GameState.Phase.FINISHED, gameState.getPhase());
    }

    @Test
    @Tag("FR3.1")
    void finishMatchReturnsFalseFromWaitingOrPaused() {
        GameState waitingState = new GameState();
        assertFalse(waitingState.finishMatch(), "finishMatch should fail from WAITING");
        assertEquals(GameState.Phase.WAITING, waitingState.getPhase());

        GameState pausedState = buildPausedState();
        assertFalse(pausedState.finishMatch(), "finishMatch should fail from PAUSED");
        assertEquals(GameState.Phase.PAUSED, pausedState.getPhase());
    }

    @Test
    void invalidTransitionsAreBlocked() {
        GameState gameState = new GameState();

        // WAITING -> can't pause, resume, beginPlaying, or finish
        assertFalse(gameState.pauseMatch());
        assertFalse(gameState.resumeMatch());
        assertFalse(gameState.beginPlaying());
        assertFalse(gameState.finishMatch());
        assertEquals(GameState.Phase.WAITING, gameState.getPhase(), 
                     "Phase should remain WAITING after all invalid transitions");
    }

    private GameState buildPlayingState() {
        GameState gameState = new GameState();
        gameState.addPlayer(new Player(CONNECTION_ID_1, PLAYER_1_NAME));
        gameState.addPlayer(new Player(CONNECTION_ID_2, PLAYER_2_NAME));
        gameState.startMatch();
        gameState.beginPlaying();
        return gameState;
    }

    private GameState buildPausedState() {
        GameState gameState = buildPlayingState();
        gameState.pauseMatch();
        return gameState;
    }
        // â”€â”€ Phase guard tests (issue #14 code review) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    
        @Test
        void pauseMatchDoesNothingWhenAlreadyPaused() {
            GameState gs = new GameState();
            gs.setPhase(GameState.Phase.PAUSED);
            gs.pauseMatch();
            assertEquals(GameState.Phase.PAUSED, gs.getPhase());
        }
    
        @Test
        void resumeMatchDoesNothingWhenPlaying() {
            GameState gs = new GameState();
            gs.setPhase(GameState.Phase.PLAYING);
            gs.resumeMatch();
            assertEquals(GameState.Phase.PLAYING, gs.getPhase());
        }
    

    
        @Test
        void startMatchDoesNothingWhenAlreadyPlaying() {
            GameState gs = new GameState();
            gs.addPlayer(new Player(CONNECTION_ID_1, PLAYER_1_NAME));
            gs.addPlayer(new Player(CONNECTION_ID_2, PLAYER_2_NAME));
            gs.setPhase(GameState.Phase.PLAYING);
            gs.startMatch();
            assertEquals(GameState.Phase.PLAYING, gs.getPhase());
        }
    

    
        // â”€â”€ Phase frozen after FINISHED â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    
        @Test
        void pauseMatchDoesNothingAfterFinished() {
            GameState gs = new GameState();
            gs.setPhase(GameState.Phase.FINISHED);
            gs.pauseMatch();
            assertEquals(GameState.Phase.FINISHED, gs.getPhase());
        }
    
        @Test
        void resumeMatchDoesNothingAfterFinished() {
            GameState gs = new GameState();
            gs.setPhase(GameState.Phase.FINISHED);
            gs.resumeMatch();
            assertEquals(GameState.Phase.FINISHED, gs.getPhase());
        }
    
        @Test
        void startMatchDoesNothingAfterFinished() {
            GameState gs = new GameState();
            gs.addPlayer(new Player(CONNECTION_ID_1, PLAYER_1_NAME));
            gs.addPlayer(new Player(CONNECTION_ID_2, PLAYER_2_NAME));
            gs.setPhase(GameState.Phase.FINISHED);
            gs.startMatch();
            assertEquals(GameState.Phase.FINISHED, gs.getPhase());
        }
    
        @Test
        void doubleFinishMatchIsSafe() {
            GameState gs = new GameState();
            gs.setPhase(GameState.Phase.PLAYING);
            gs.finishMatch();
            gs.finishMatch();
            assertEquals(GameState.Phase.FINISHED, gs.getPhase());
        }
    
        // â”€â”€ Full lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    
        @Test
        void fullLifecycleFlow() {
            GameState gs = new GameState();
            gs.addPlayer(new Player(CONNECTION_ID_1, PLAYER_1_NAME));
            gs.addPlayer(new Player(CONNECTION_ID_2, PLAYER_2_NAME));
            assertEquals(GameState.Phase.WAITING, gs.getPhase());
            gs.startMatch();
            assertEquals(GameState.Phase.PLAYING, gs.getPhase());
            gs.beginPlaying();
            assertEquals(GameState.Phase.PLAYING, gs.getPhase());
            gs.pauseMatch();
            assertEquals(GameState.Phase.PAUSED, gs.getPhase());
            gs.resumeMatch();
            assertEquals(GameState.Phase.PLAYING, gs.getPhase());
            gs.finishMatch();
            assertEquals(GameState.Phase.FINISHED, gs.getPhase());
            gs.pauseMatch();
            assertEquals(GameState.Phase.FINISHED, gs.getPhase());
        }
}
