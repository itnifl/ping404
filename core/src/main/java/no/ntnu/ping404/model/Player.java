package no.ntnu.ping404.model;

import no.ntnu.ping404.utils.Constants;

/**
 * Represents a player in the game.
 * Tracks identity, position, velocity, and score.
 */
public class Player {

    private int id;
    private String name;
    private float x;
    private float y;
    private float paddleY;
    private float paddleSpeed;
    private int score;
    private boolean ready;

    /** No-arg constructor required for Kryo serialization. */
    public Player() {
    }

    public Player(int id, String name) {
        this.id = id;
        this.name = name;
        this.score = 0;
        this.ready = false;
        this.paddleSpeed = Constants.PADDLE_DEFAULT_SPEED;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public float getPaddleY() { return paddleY; }
    public void setPaddleY(float paddleY) { this.paddleY = paddleY; }

    public float getPaddleSpeed() { return paddleSpeed; }
    public void setPaddleSpeed(float paddleSpeed) { this.paddleSpeed = paddleSpeed; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public void incrementScore() { this.score++; }

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    /**
     * Reset player state for a new round.
     */
    public void resetForNewRound(float startX, float startY) {
        this.x = startX;
        this.y = startY;
        this.paddleY = startY;
    }

    public void movePaddleUp(float deltaTime) {
        float nextY = paddleY + (paddleSpeed * deltaTime);
        float limit = Constants.DEFAULT_FIELD_HEIGHT - (Constants.PADDLE_HEIGHT / 2);
        this.paddleY = Math.min(nextY, limit);
    }

    public void movePaddleDown(float deltaTime) {
        float nextY = paddleY - (paddleSpeed * deltaTime);
        float limit = Constants.PADDLE_HEIGHT / 2;
        this.paddleY = Math.max(nextY, limit);
    }

    @Override
    public String toString() {
        return "Player{id=" + id + ", name='" + name + "', score=" + score + "}";
    }
}
