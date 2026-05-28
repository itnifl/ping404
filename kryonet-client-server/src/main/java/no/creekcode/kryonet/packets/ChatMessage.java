package no.creekcode.kryonet.packets;

/** Bidirectional: chat message. */
public class ChatMessage {

    public int senderId;
    public String senderName;
    public String message;
    public long timestamp;
    public MessageType type;

    public enum MessageType {
        NORMAL, SYSTEM, WHISPER, EMOTE
    }

    public ChatMessage() {
        this.type = MessageType.NORMAL;
    }

    public static ChatMessage player(int senderId, String senderName, String message) {
        ChatMessage c = new ChatMessage();
        c.senderId = senderId;
        c.senderName = senderName;
        c.message = message;
        c.timestamp = System.currentTimeMillis();
        c.type = MessageType.NORMAL;
        return c;
    }

    public static ChatMessage system(String message) {
        ChatMessage c = new ChatMessage();
        c.senderId = -1;
        c.senderName = "System";
        c.message = message;
        c.timestamp = System.currentTimeMillis();
        c.type = MessageType.SYSTEM;
        return c;
    }

    @Override
    public String toString() {
        return "ChatMessage{sender='" + senderName + "', message='" + message + "'}";
    }
}
