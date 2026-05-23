package no.ntnu.ping404.server.game;

/**
 * Represents a player input received from the network thread.
 * Part of the Producer-Consumer pattern required by issue #14.
 *
 * Producer: Network thread creates InputEvents when receiving client packets.
 * Consumer: GameLoop thread drains and processes these events each tick.
 */
public class InputEvent {

    public enum Type {
        PADDLE_MOVE,
        PAUSE_REQUEST,
        RESUME_REQUEST
    }

    private final int connectionId;
    private final Type type;
    private final float x;
    private final float y;

    private InputEvent(int connectionId, Type type, float x, float y) {
        this.connectionId = connectionId;
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public static InputEvent paddleMove(int connectionId, float x, float y) {
        return new InputEvent(connectionId, Type.PADDLE_MOVE, x, y);
    }

    public static InputEvent pauseRequest(int connectionId) {
        return new InputEvent(connectionId, Type.PAUSE_REQUEST, 0, 0);
    }

    public static InputEvent resumeRequest(int connectionId) {
        return new InputEvent(connectionId, Type.RESUME_REQUEST, 0, 0);
    }

    public int getConnectionId() { return connectionId; }
    public Type getType() { return type; }
    public float getX() { return x; }
    public float getY() { return y; }

    @Override
    public String toString() {
        return "InputEvent{connId=" + connectionId + ", type=" + type + ", x=" + x + ", y=" + y + "}";
    }
}
