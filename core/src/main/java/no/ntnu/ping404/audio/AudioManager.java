package no.ntnu.ping404.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;

/**
 * Singleton audio manager for PING-404.
 *
 * <p>Covers audio requirements:</p>
 * <ul>
 *   <li>Issue #52 - menu button sounds and background music</li>
 *   <li>Issue #53 - puck hit and goal sounds</li>
 *   <li>Issue #54 - mallet hit sounds</li>
 *   <li>Issue #55 - background game music</li>
 *   <li>Issue #56 - game event sounds: countdown, game start, game over</li>
 * </ul>
 *
 * <p>All public methods are null-safe: they are no-ops when the manager has not
 * been initialized (e.g., in unit tests that run without a LibGDX context).</p>
 *
 * <p>Lifecycle: call {@link #initialize()} once in {@code Game.create()},
 * and {@link #dispose()} in {@code Game.dispose()}.</p>
 */
public class AudioManager {

    private static AudioManager instance;

    private static final float DEFAULT_SOUND_VOLUME = 1.0f;
    private static final float MENU_MUSIC_VOLUME = 0.5f;
    private static final float GAME_MUSIC_VOLUME = 0.3f;
    private static final float COUNTDOWN_TICK_VOLUME_MULTIPLIER = 0.7f;
    private static final float GOAL_SOUND_VOLUME_MULTIPLIER = 1.2f;

    private static final String PREFS_NAME = "ping404_audio";
    private static final String PREF_SOUND_ENABLED = "sound_enabled";
    private static final String PREF_MUSIC_ENABLED = "music_enabled";

    private Preferences prefs;

    private Sound menuSelectSound;
    private Sound paddleHitSound;
    private Sound score1Sound;
    private Sound score2Sound;
    private Sound lossSound;
    private Sound errorSound;
    private Sound warningSound;
    private Music menuMusic;
    private Music gameMusic1;
    private Music gameMusic2;
    private Music currentMusic;

    private boolean soundEnabled = true;
    private boolean musicEnabled = true;
    private float soundVolume = DEFAULT_SOUND_VOLUME;

    private AudioManager() {
        prefs = Gdx.app != null ? Gdx.app.getPreferences(PREFS_NAME) : null;
        if (prefs != null) {
            soundEnabled = prefs.getBoolean(PREF_SOUND_ENABLED, true);
            musicEnabled = prefs.getBoolean(PREF_MUSIC_ENABLED, true);
        }
        menuSelectSound = loadSound("menuselect.wav");
        paddleHitSound = loadSound("paddlehit.wav");
        score1Sound = loadSound("score1.wav");
        score2Sound = loadSound("score2.wav");
        lossSound = loadSound("loss1.wav");
        errorSound = loadSound("error.wav");
        warningSound = loadSound("warning.wav");
        menuMusic = loadMusic("menumusic1.wav");
        gameMusic1 = loadMusic("gamemusic1.wav");
        gameMusic2 = loadMusic("gamemusic2.wav");
    }

    /**
     * Initializes the AudioManager. Idempotent - safe to call multiple times.
     * Must be called after LibGDX has set up its audio backend.
     */
    public static void initialize() {
        if (instance == null) {
            instance = new AudioManager();
        }
    }

    /**
     * Returns {@code true} if the manager has been initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Releases all audio resources and resets the singleton.
     * Call from {@code Game.dispose()}.
     */
    public static void dispose() {
        if (instance != null) {
            if (instance.menuSelectSound != null) instance.menuSelectSound.dispose();
            if (instance.paddleHitSound != null) instance.paddleHitSound.dispose();
            if (instance.score1Sound != null) instance.score1Sound.dispose();
            if (instance.score2Sound != null) instance.score2Sound.dispose();
            if (instance.lossSound != null) instance.lossSound.dispose();
            if (instance.errorSound != null) instance.errorSound.dispose();
            if (instance.warningSound != null) instance.warningSound.dispose();
            if (instance.menuMusic != null) instance.menuMusic.dispose();
            if (instance.gameMusic1 != null) instance.gameMusic1.dispose();
            if (instance.gameMusic2 != null) instance.gameMusic2.dispose();
            instance = null;
        }
    }

    /** Plays the menu button selection click sound (Issue #52). */
    public static void playMenuSelect() {
        if (instance != null && instance.soundEnabled && instance.menuSelectSound != null) {
            instance.menuSelectSound.play(instance.soundVolume);
        }
    }

    /** Plays the paddle/mallet hit sound (Issues #53, #54). */
    public static void playPaddleHit() {
        if (instance != null && instance.soundEnabled && instance.paddleHitSound != null) {
            instance.paddleHitSound.play(instance.soundVolume);
        }
    }

    /** Plays a random goal scored sound (Issue #53). */
    public static void playGoalSound() {
        if (instance == null || !instance.soundEnabled) {
            return;
        }
        Sound sound = MathUtils.randomBoolean() ? instance.score1Sound : instance.score2Sound;
        if (sound != null) {
            sound.play(instance.soundVolume * GOAL_SOUND_VOLUME_MULTIPLIER);
        }
    }

    /** Plays the error sound (e.g. 7-second puck stall reset). */
    public static void playError() {
        if (instance != null && instance.soundEnabled && instance.errorSound != null) {
            instance.errorSound.play(instance.soundVolume);
        }
    }

    /** Plays the warning sound (e.g. action blocked due to missing name). */
    public static void playWarning() {
        if (instance != null && instance.soundEnabled && instance.warningSound != null) {
            instance.warningSound.play(instance.soundVolume);
        }
    }

    /** Plays the loss sound when opponent scores against this player. */
    public static void playLossSound() {
        if (instance != null && instance.soundEnabled && instance.lossSound != null) {
            instance.lossSound.play(instance.soundVolume * GOAL_SOUND_VOLUME_MULTIPLIER);
        }
    }

    /** Plays a single countdown tick beep (Issue #56). */
    public static void playCountdownTick() {
        if (instance != null && instance.soundEnabled && instance.menuSelectSound != null) {
            instance.menuSelectSound.play(instance.soundVolume * COUNTDOWN_TICK_VOLUME_MULTIPLIER);
        }
    }

    /** Plays the game start sound when the countdown finishes (Issue #56). */
    public static void playGameStart() {
        if (instance != null && instance.soundEnabled && instance.menuSelectSound != null) {
            instance.menuSelectSound.play(instance.soundVolume);
        }
    }

    /** Plays the game over stinger (Issue #56). */
    public static void playGameOver() {
        if (instance != null && instance.soundEnabled && instance.menuSelectSound != null) {
            instance.menuSelectSound.play(instance.soundVolume);
        }
    }

    /**
     * Starts looping background music suited to the main menu (Issues #52, #55).
     * Stops any game music first.
     */
    public static void startMenuMusic() {
        if (instance == null || !instance.musicEnabled || instance.menuMusic == null) {
            return;
        }
        stopMusic();
        instance.currentMusic = instance.menuMusic;
        instance.currentMusic.setLooping(true);
        instance.currentMusic.setVolume(MENU_MUSIC_VOLUME);
        instance.currentMusic.play();
    }

    /**
     * Starts looping background music suited to the game (Issue #55).
     * Randomly selects between gamemusic1 and gamemusic2.
     * Stops menu music first.
     */
    public static void startGameMusic() {
        if (instance == null || !instance.musicEnabled) {
            return;
        }
        stopMusic();
        Music selectedMusic = MathUtils.randomBoolean() ? instance.gameMusic1 : instance.gameMusic2;
        if (selectedMusic == null) {
            return;
        }
        instance.currentMusic = selectedMusic;
        instance.currentMusic.setLooping(true);
        instance.currentMusic.setVolume(GAME_MUSIC_VOLUME);
        instance.currentMusic.play();
    }

    /** Stops all background music. */
    public static void stopMusic() {
        if (instance == null) {
            return;
        }
        if (instance.currentMusic != null) {
            instance.currentMusic.stop();
            instance.currentMusic = null;
        }
    }

    /** Pauses the currently playing background music. */
    public static void pauseMusic() {
        if (instance != null && instance.currentMusic != null && instance.currentMusic.isPlaying()) {
            instance.currentMusic.pause();
        }
    }

    /** Resumes background music if music is enabled and music was paused. */
    public static void resumeMusic() {
        if (instance != null && instance.musicEnabled && instance.currentMusic != null
                && !instance.currentMusic.isPlaying()) {
            instance.currentMusic.play();
        }
    }

    /** Enables or disables short sound effects. Stopping ongoing sounds is best-effort. */
    public static void setSoundEnabled(boolean enabled) {
        if (instance != null) {
            instance.soundEnabled = enabled;
            if (instance.prefs != null) {
                instance.prefs.putBoolean(PREF_SOUND_ENABLED, enabled);
                instance.prefs.flush();
            }
        }
    }

    /**
     * Enables or disables background music.
     * Disabling immediately stops any playing track.
     */
    public static void setMusicEnabled(boolean enabled) {
        if (instance != null) {
            instance.musicEnabled = enabled;
            if (!enabled) {
                stopMusic();
            }
            if (instance.prefs != null) {
                instance.prefs.putBoolean(PREF_MUSIC_ENABLED, enabled);
                instance.prefs.flush();
            }
        }
    }

    /** Returns whether sound effects are currently enabled. */
    public static boolean isSoundEnabled() {
        return instance != null && instance.soundEnabled;
    }

    /** Returns whether background music is currently enabled. */
    public static boolean isMusicEnabled() {
        return instance != null && instance.musicEnabled;
    }



    private static Sound loadSound(String path) {
        if (Gdx.audio == null) {
            return null;
        }
        try {
            return Gdx.audio.newSound(Gdx.files.internal(path));
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.log("AudioManager", "Could not load sound: " + path + " - " + e.getMessage());
            }
            return null;
        }
    }

    private static Music loadMusic(String path) {
        if (Gdx.audio == null) {
            return null;
        }
        try {
            return Gdx.audio.newMusic(Gdx.files.internal(path));
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.log("AudioManager", "Could not load music: " + path + " - " + e.getMessage());
            }
            return null;
        }
    }
}
