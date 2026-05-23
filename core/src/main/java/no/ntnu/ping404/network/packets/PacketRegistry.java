package no.ntnu.ping404.network.packets;

import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryo.Kryo;
import no.ntnu.ping404.model.GameState;

public final class PacketRegistry {

    private PacketRegistry() {}

    public static void register(Kryo kryo) {
        kryo.register(byte[].class);
        kryo.register(int[].class);
        kryo.register(float[].class);
        kryo.register(String[].class);
        kryo.register(LoginRequest.class);
        kryo.register(LoginResponse.class);
        kryo.register(PlayerPosition.class);
        kryo.register(ChatMessage.class);
        kryo.register(ChatMessage.MessageType.class);
        kryo.register(PlayerJoined.class);
        kryo.register(PlayerLeft.class);
        kryo.register(PlayerList.class);
        kryo.register(PlayerList.PlayerEntry.class);
        kryo.register(Ping.class);
        kryo.register(Pong.class);
        kryo.register(PauseRequest.class);
        kryo.register(PauseEvent.class);
        kryo.register(ResumeRequest.class);
        kryo.register(ResumeEvent.class);
        kryo.register(GameStartRequest.class);
        kryo.register(GameStartEvent.class);
        kryo.register(ErrorPacket.class);
        kryo.register(HostMigration.class);
        kryo.register(GameReset.class);
        kryo.register(LeaveRoom.class);
        kryo.register(java.util.ArrayList.class);
        kryo.register(GameOver.class);
        kryo.register(java.util.HashMap.class);
        kryo.register(GoalScored.class);
        kryo.register(ScoreUpdate.class); 
        kryo.register(GameStateSnapshot.class);
        kryo.register(RoomMetricsSnapshot.class);
        kryo.register(GameState.Phase.class);
        kryo.register(Vector2.class);
        kryo.register(RematchRequest.class);
        kryo.register(RematchStart.class);
    }
}
