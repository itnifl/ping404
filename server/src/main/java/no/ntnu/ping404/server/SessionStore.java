package no.ntnu.ping404.server;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Manages session tokens for player reconnection.
 * When a player logs in, a token is created and stored.
 * If the player disconnects, they can reconnect using the token within the timeout window.
 */
public class SessionStore {

    private record SessionEntry(int connectionId, String roomId, long expiresAtMs) {}

    /** 
     * Maps session tokens to their associated connection ID, room ID, and expiration timestamp.
     * ConcurrentHashMap allows thread-safe access from multiple handlers and the disconnect handler without external synchronization.
     **/
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final LongSupplier clock;
    private final long timeoutMs;

    /** Default timeout window for reconnection (30 seconds). */
    public static final long DEFAULT_TIMEOUT_MS = 30_000;

    public SessionStore() {
        this(System::currentTimeMillis, DEFAULT_TIMEOUT_MS);
    }

    /** Package-private constructor allowing test code to inject a controllable clock. */
    SessionStore(LongSupplier clock) {
        this(clock, DEFAULT_TIMEOUT_MS);
    }

    /** Package-private constructor allowing test code to inject both clock and timeout. */
    SessionStore(LongSupplier clock, long timeoutMs) {
        this.clock = clock;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    /**
     * Create a new session token for the given connection and room.
     *
     * @param connectionId the player's connection ID
     * @param roomId       the room the player was in
     * @return a unique session token
     */
    public String createSession(int connectionId, String roomId) {
        String token = this.generateSessionToken();
        sessions.put(token, new SessionEntry(connectionId, roomId, clock.getAsLong() + timeoutMs));
        return token;
    }

    /**
     * Generate a secure random session token.
     * The role of the token is to allow a disconnected player to reconnect to their game within a short time window.
     * The token is a URL-safe Base64 string derived from 32 random bytes, providing
     * enough entropy to prevent guessing attacks while being compact for network transmission and KryoNet serialization.
     * @return a unique session token
    */
    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // Encodes random bytes into a compact ASCII token string without '=' padding; safe for KryoNet packet serialization.
    }

    /**
     * Check whether a token is still valid (exists and not expired).
     *
     * @param token the session token
     * @return true if the token is valid and within the timeout window
     */
    public boolean isValid(String token) {
        // lazy expiry, check expiry on every lookup
        SessionEntry entry = sessions.get(token);
        return entry != null && entry.expiresAtMs() > clock.getAsLong();
    }

    /**
     * Get the room ID associated with a token.
     *
     * @param token the session token
     * @return the room ID, or null if the token is invalid
     */
    public String getRoomId(String token) {
        SessionEntry entry = sessions.get(token);
        if (entry == null || entry.expiresAtMs() <= clock.getAsLong()) return null;
        return entry.roomId();
    }

    /**
     * Get the original connection ID associated with a token.
     *
     * @param token the session token
     * @return the connection ID, or -1 if the token is invalid
     */
    public int getConnectionId(String token) {
        SessionEntry entry = sessions.get(token);
        if (entry == null || entry.expiresAtMs() <= clock.getAsLong()) return -1;
        return entry.connectionId();
    }

    /**
     * Refresh timeout for active session(s) owned by this connection.
     * Extends expiry to now + DEFAULT_TIMEOUT_MS only for non-expired entries.
     *
     * @param connectionId active connection ID
     */
    public void refreshByConnectionId(int connectionId) {
        long now = clock.getAsLong();
        long newExpiry = now + timeoutMs;
        for (Map.Entry<String, SessionEntry> entry : sessions.entrySet()) {
            SessionEntry value = entry.getValue();
            if (value.connectionId() == connectionId && value.expiresAtMs() > now) {
                sessions.replace(entry.getKey(), value,
                        new SessionEntry(value.connectionId(), value.roomId(), newExpiry));
            }
        }
    }

    /**
     * Invalidate a specific session token (e.g. after successful reconnect or match end).
     *
     * @param token the session token to invalidate
     */
    public void invalidate(String token) {
        sessions.remove(token);
    }

    /**
     * Invalidate all sessions for a specific connection ID (e.g. when player intentionally leaves).
     *
     * @param connectionId the connection ID whose sessions should be invalidated
     */
    public void invalidateByConnectionId(int connectionId) {
        sessions.entrySet().removeIf(e -> e.getValue().connectionId() == connectionId);
    }

    /**
     * Remove all expired sessions from the store.
     */
    public void invalidateExpired() {
        long now = clock.getAsLong();
        sessions.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }

    /**
     * Remove all expired sessions and return the connection IDs that were expired.
     * Used to clean up disconnected slots in rooms after the reconnect window closes.
     *
     * @return a set of connection IDs whose sessions just expired
     */
    public Set<Integer> invalidateExpiredAndGetConnectionIds() {
        return invalidateExpiredAndGetExpiredSessions().keySet();
    }

    /**
     * Remove all expired sessions and return a map of connection ID to room ID.
     * Used to reset game phase when a player's reconnect window expires.
     *
     * @return a map of expired connection IDs to their room IDs
     */
    public Map<Integer, String> invalidateExpiredAndGetExpiredSessions() {
        Map<Integer, String> expired = new HashMap<>();
        long now = clock.getAsLong();
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().expiresAtMs() <= now) {
                expired.put(e.getValue().connectionId(), e.getValue().roomId());
                return true;
            }
            return false;
        });
        return expired;
    }
}
