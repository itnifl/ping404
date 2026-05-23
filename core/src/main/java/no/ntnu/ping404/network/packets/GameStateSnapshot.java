package no.ntnu.ping404.network.packets;

import com.badlogic.gdx.math.Vector2;
import no.ntnu.ping404.model.GameState;

/**
 * Server -> Client: Authoritative snapshot of current match state.
 *
 * Used as interpolation source on the client side.
 */
public class GameStateSnapshot {

	/** Puck center position */
	public Vector2 puckPosition;

	/** Puck velocity */
	public Vector2 puckVelocity;

	/** Player 1 mallet position */
	public Vector2 player1Position;

	/** Player 2 mallet position */
	public Vector2 player2Position;

	/** Current scoreboard */
	public int player1Score;
	public int player2Score;

	/** Match phase */
	public GameState.Phase phase;

	/** Timestamp used for interpolation and staleness checks */
	public long timestamp;

	/** True when the server reset the puck due to the 7-second stall rule (FR2.6). */
	public boolean puckStallReset;

	/** Required for Kryo serialization */
	public GameStateSnapshot() {
	}

	public GameStateSnapshot(
		Vector2 puckPosition,
		Vector2 puckVelocity,
		Vector2 player1Position,
		Vector2 player2Position,
		int player1Score,
		int player2Score,
		GameState.Phase phase
	) {
		this.puckPosition = puckPosition;
		this.puckVelocity = puckVelocity;
		this.player1Position = player1Position;
		this.player2Position = player2Position;
		this.player1Score = player1Score;
		this.player2Score = player2Score;
		this.phase = phase;
		this.timestamp = System.currentTimeMillis();
	}

	@Override
	public String toString() {
		return "GameStateSnapshot{"
			+ "puck=" + puckPosition + ", "
			+ "puckV=" + puckVelocity + ", "
			+ "p1=" + player1Position + ", "
			+ "p2=" + player2Position + ", "
			+ "score=" + player1Score + "-" + player2Score + ", "
			+ "phase=" + phase
			+ "}";
	}
}
