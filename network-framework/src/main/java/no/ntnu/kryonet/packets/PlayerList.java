package no.ntnu.kryonet.packets;

import java.util.ArrayList;
import java.util.List;

/** Server to client: full list of currently connected players, sent after login. */
public class PlayerList {

    public List<PlayerEntry> players;

    public PlayerList() {
        this.players = new ArrayList<>();
    }

    public void addPlayer(int playerId, String playerName, float x, float y) {
        players.add(new PlayerEntry(playerId, playerName, x, y));
    }

    public static class PlayerEntry {
        public int playerId;
        public String playerName;
        public float x;
        public float y;

        public PlayerEntry() {}

        public PlayerEntry(int playerId, String playerName, float x, float y) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public String toString() {
        return "PlayerList{count=" + players.size() + "}";
    }
}
