package no.ntnu.ping404.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Manages persistent client preferences using LibGDX Preferences API.
 * Stores display name, last server, win score, and session reconnection data.
 *
 * <p>Sound/music preferences are handled by {@link no.ntnu.ping404.audio.AudioManager}.</p>
 *
 * <p>Session token and room code are stored for reconnection support (FR4.2, A1).</p>
 *
 * <p>All setters persist immediately. Values survive app restarts.</p>
 */
public class PreferencesManager {

    private static final String PREFS_NAME = "ping404_settings";

    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_LAST_SERVER = "last_server";
    private static final String KEY_WIN_SCORE = "win_score";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_SESSION_ROOM = "session_room";

    private static final String DEFAULT_DISPLAY_NAME = "";
    /** Default server IP used when no saved preference exists. */
    public static final String DEFAULT_SERVER_IP = "127.0.0.1";
    private static final int DEFAULT_WIN_SCORE = 5;
    private static final int MIN_WIN_SCORE = 1;
    private static final int MAX_WIN_SCORE = 15;

    private static volatile PreferencesManager instance;

    private final Preferences prefs;

    private PreferencesManager() {
        this.prefs = Gdx.app.getPreferences(PREFS_NAME);
    }

    /**
     * Returns the singleton instance. Creates it on first call.
     * Thread-safe using double-checked locking.
     * Must be called after LibGDX is initialized.
     */
    public static PreferencesManager getInstance() {
        if (instance == null) {
            synchronized (PreferencesManager.class) {
                if (instance == null) {
                    instance = new PreferencesManager();
                }
            }
        }
        return instance;
    }

    /**
     * Resets the singleton. Call from {@code Game.dispose()} or tests.
     */
    public static void reset() {
        instance = null;
    }

    // Display Name

    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME);
    }

    public void setDisplayName(String name) {
        prefs.putString(KEY_DISPLAY_NAME, name != null ? name.trim() : DEFAULT_DISPLAY_NAME);
        prefs.flush();
    }

    // Last Server

    public String getLastServer() {
        return prefs.getString(KEY_LAST_SERVER, DEFAULT_SERVER_IP);
    }

    public void setLastServer(String server) {
        prefs.putString(KEY_LAST_SERVER, server != null ? server.trim() : DEFAULT_SERVER_IP);
        prefs.flush();
    }

    // Win Score

    private int clampWinScore(int score) {
        return Math.max(MIN_WIN_SCORE, Math.min(MAX_WIN_SCORE, score));
    }

    public int getWinScore() {
        return prefs.getInteger(KEY_WIN_SCORE, DEFAULT_WIN_SCORE);
    }

    public void setWinScore(int score) {
        int clamped = clampWinScore(score);
        prefs.putInteger(KEY_WIN_SCORE, clamped);
        prefs.flush();
    }

    // Session Token and Room (FR4.2, A1 - reconnection support)

    private String getPreferenceOrNull(String key) {
        String value = prefs.getString(key, "");
        return value.isEmpty() ? null : value;
    }

    private void setPreferenceOrRemove(String key, String value) {
        if (value == null || value.isEmpty()) {
            prefs.remove(key);
        } else {
            prefs.putString(key, value);
        }
        prefs.flush();
    }

    /**
     * Returns the stored session token for reconnection, or null if none exists.
     *
     * @return the session token, or null
     */
    public String getSessionToken() {
        return getPreferenceOrNull(KEY_SESSION_TOKEN);
    }

    /**
     * Stores the session token received from the server for reconnection.
     *
     * @param token the session token, or null to clear
     */
    public void setSessionToken(String token) {
        setPreferenceOrRemove(KEY_SESSION_TOKEN, token);
    }

    /**
     * Returns the room code associated with the stored session.
     *
     * @return the room code, or null if none
     */
    public String getSessionRoom() {
        return getPreferenceOrNull(KEY_SESSION_ROOM);
    }

    /**
     * Stores the room code for reconnection.
     *
     * @param roomCode the room code, or null to clear
     */
    public void setSessionRoom(String roomCode) {
        setPreferenceOrRemove(KEY_SESSION_ROOM, roomCode);
    }

    /**
     * Clears the stored session (token and room).
     * Call when the game ends normally or session expires.
     */
    public void clearSession() {
        prefs.remove(KEY_SESSION_TOKEN);
        prefs.remove(KEY_SESSION_ROOM);
        prefs.flush();
    }

    /**
     * Clears all stored preferences. Intended for testing or user-initiated reset.
     * Use {@link #clearSession()} for normal session cleanup.
     */
    public void clear() {
        prefs.clear();
        prefs.flush();
    }
}
