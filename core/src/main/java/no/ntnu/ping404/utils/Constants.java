package no.ntnu.ping404.utils;

/**
 * Game constants and configuration values.
 */
public class Constants {
    private Constants() {
    }

    // Board dimensions (authoritative simulation space)
    public static final float BOARD_MIN_X = 0f;
    public static final float BOARD_MIN_Y = 0f;
    public static final float BOARD_WIDTH = 800f;
    public static final float BOARD_HEIGHT = 480f;

    // Viewport dimensions used by LibGDX screens.
    public static final float WORLD_WIDTH = 480f;
    public static final float WORLD_HEIGHT = 800f;

    // Menu viewport (portrait orientation)
    public static final float MENU_WIDTH = 480f;
    public static final float MENU_HEIGHT = 800f;

    // Game viewport (landscape orientation, matches board)
    public static final float GAME_WIDTH = BOARD_WIDTH;
    public static final float GAME_HEIGHT = BOARD_HEIGHT;

    // Physical window sizes (pixel dimensions passed to setWindowedMode).
    // These are larger than the internal world units to look sharp on screen.
    public static final int GAME_WINDOW_WIDTH = 960;
    public static final int GAME_WINDOW_HEIGHT = 540;
    public static final int MENU_WINDOW_WIDTH = (int) MENU_WIDTH;
    public static final int MENU_WINDOW_HEIGHT = (int) MENU_HEIGHT;

    // Table / play-area dimensions for horizontal game rendering.
    // Table is centered in the game viewport.
    public static final float TABLE_MARGIN = 20f;
    public static final float TABLE_X = TABLE_MARGIN;
    public static final float TABLE_Y = TABLE_MARGIN;
    public static final float TABLE_WIDTH = GAME_WIDTH - (2 * TABLE_MARGIN);
    public static final float TABLE_HEIGHT = GAME_HEIGHT - (2 * TABLE_MARGIN);
    public static final float CENTER_X = TABLE_X + (TABLE_WIDTH / 2f);
    public static final float CENTER_Y = TABLE_Y + (TABLE_HEIGHT / 2f);

    // Paddle properties (Keep these for other screens)
    public static final float PADDLE_WIDTH = 15f;
    public static final float PADDLE_HEIGHT = 80f;

    // Puck and mallet properties (Updated/New)
    public static final float MALLET_RADIUS = 28f;

    // Goal dimensions
    public static final float GOAL_WIDTH = 120f; 
    public static final float GOAL_HEIGHT = 10f; // Thickness

    //Winning conditions
    public static final int WINNING_SCORE = 5;
    public static final float PADDLE_MARGIN = 20f; // Distance from the side wall
    public static final float PADDLE_DEFAULT_SPEED = 400f;

    // Puck properties
    public static final float PUCK_RADIUS = 8f;
    public static final float INITIAL_PUCK_SPEED = 300f;
    public static final float PUCK_SUBSTEP_DISTANCE_FACTOR = 0.75f; // Fraction of radius for anti-tunneling substep size

    // Puck stall detection (FR2.6)
    /** Time in milliseconds before puck is reset if it stays on one half. */
    public static final long PUCK_STALL_TIMEOUT_MS = 7000L;
    /** Time in seconds before puck is reset if it stays on one half. */
    public static final float PUCK_STALL_TIMEOUT_SECONDS = 7.0f;

    // Backward-compatible aliases (use BOARD_* in new code).
    public static final float DEFAULT_FIELD_WIDTH = BOARD_WIDTH;
    public static final float DEFAULT_FIELD_HEIGHT = BOARD_HEIGHT;

    public static float boardCenterX() {
        return BOARD_MIN_X + BOARD_WIDTH / 2f;
    }

    public static float boardCenterY() {
        return BOARD_MIN_Y + BOARD_HEIGHT / 2f;
    }

    /**
     * X-coordinate of the centre of Player 1's half (left half).
     * Used for puck relaunch after Player 1 concedes a goal (FR2.10).
     */
    public static float player1HalfCenterX() {
        return BOARD_WIDTH / 4f;
    }

    /**
     * X-coordinate of the centre of Player 2's half (right half).
     * Used for puck relaunch after Player 2 concedes a goal (FR2.10).
     */
    public static float player2HalfCenterX() {
        return BOARD_WIDTH * 3f / 4f;
    }

    /**
     * Returns the top Y-coordinate of the goal opening (center + GOAL_WIDTH/2).
     * The goal opening spans vertically from goalBottom to goalTop.
     *
     * @param fieldHeight the field height
     * @return the top Y-coordinate of the goal opening
     */
    public static float goalTop(float fieldHeight) {
        return (fieldHeight / 2f) + (GOAL_WIDTH / 2f);
    }

    /**
     * Returns the bottom Y-coordinate of the goal opening (center - GOAL_WIDTH/2).
     * The goal opening spans vertically from goalBottom to goalTop.
     *
     * @param fieldHeight the field height
     * @return the bottom Y-coordinate of the goal opening
     */
    public static float goalBottom(float fieldHeight) {
        return (fieldHeight / 2f) - (GOAL_WIDTH / 2f);
    }

    public static float goalTopDefaultField() {
        return goalTop(BOARD_HEIGHT);
    }

    public static float goalBottomDefaultField() {
        return goalBottom(BOARD_HEIGHT);
    }
}