package no.ntnu.ping404.screens;

import com.badlogic.gdx.math.Rectangle;

/**
 * Abstract contract for platform-specific game renderers.
 *
 * <p>Implementations provide rendering behavior tailored to Desktop or Android,
 * allowing each platform to customize UI hints, interaction feedback, and 
 * layout decisions while sharing core game-element drawing logic.</p>
 *
 * <p><b>Option 3 Architecture</b></p>
 * <p>Platform-specific features are added as interface methods, ensuring compile-time
 * type safety and clean separation. Layout constants supplement these methods for
 * dimension tweaking without code duplication.</p>
 *
 * @see DesktopGameRenderer
 * @see AndroidGameRenderer
 * @see GameRendererFactory
 */
public interface GameRendererView {

    /** Draws the table surface, center line, center circle, and goal slots. */
    void renderTable();

    /** Draws the puck with glow and inner detail at the given world position. */
    void renderPuck(float x, float y);

    /** Draws both mallets with slot-consistent colors (slot 1 blue, slot 2 red). */
    void renderMalletsBySlot(float slot1X, float slot1Y, float slot2X, float slot2Y);

    /**
     * Draws the score bar, names, and pause button.
     *
     * @param pauseFlashing true if pause button should flash red (error state)
     * @param pauseErrorMessage message to display near pause button when flashing
     */
    void renderHUD(String playerName, String opponentName, int playerScore, int opponentScore,
                   Rectangle pauseButton, float worldWidth, float worldHeight,
                   boolean pauseFlashing, String pauseErrorMessage);

    /** Draws client and server runtime metrics in a compact debug panel. */
    void renderMetricsOverlay(long clientRttMs,
                              float clientSnapshotRateHz,
                              float clientSnapshotJitterMs,
                              String serverLine1,
                              String serverLine2,
                              float worldHeight);

    /** Draws the role and debug hint text appropriate for the platform. */
    void renderRoleHint(String playerSideLabel, float worldHeight);

    /** Draws the pre-game countdown number centred on screen. */
    void renderCountdown(float timer);

    /** Draws the goal-scored flash overlay with heading, message and current score. */
    void renderGoalFlash(String heading, String message, int playerScore, int opponentScore,
                         float worldWidth, float worldHeight);

    /**
     * Draws the pause overlay panel with Resume and Quit buttons.
     *
     * @param canResume true if this player is allowed to resume (host only)
     * @param errorMessage message to display if action was denied, empty string if none
     */
    void renderPauseOverlay(Rectangle resumeButton, Rectangle quitButton,
                            boolean resumePressed, boolean quitPressed,
                            boolean canResume, String errorMessage,
                            float worldWidth, float worldHeight);

    /** Returns true if the platform supports debug keyboard shortcuts. */
    boolean supportsKeyboardDebug();

    /**
     * Renders platform-specific overlays (e.g., touch indicators on Android).
     * Called after all game elements are drawn.
     *
     * @param worldWidth  viewport width
     * @param worldHeight viewport height
     */
    void renderPlatformOverlay(float worldWidth, float worldHeight);

    /**
     * Renders platform-specific input hints when waiting for player action.
     * Desktop shows keyboard shortcuts; Android shows touch gestures.
     *
     * @param message     the action prompt (e.g., "Tap to start")
     * @param worldWidth  viewport width
     * @param worldHeight viewport height
     */
    void renderInputPrompt(String message, float worldWidth, float worldHeight);

    /** Returns the HUD bar height in world units. */
    float getHudHeight();

    /** Returns the pause button diameter in world units. */
    float getPauseButtonSize();

    /** Returns the minimum touch target size in world units (for accessibility). */
    float getMinTouchTargetSize();

    /** Returns the metrics panel width in world units. */
    float getMetricsPanelWidth();

    /** Returns the metrics panel height in world units. */
    float getMetricsPanelHeight();
}
