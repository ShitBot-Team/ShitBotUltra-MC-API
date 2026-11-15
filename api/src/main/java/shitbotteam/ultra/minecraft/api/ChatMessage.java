package shitbotteam.ultra.minecraft.api;

public final class ChatMessage {
    private final Player player;
    private final String text;
    private final String originId;
    public ChatMessage(Player player, String text, String originId) {
        Protocol.text(text, "chat", 16000);
        Protocol.text(originId, "origin ID", 1024);
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.text = java.util.Objects.requireNonNull(text, "text");
        this.originId = java.util.Objects.requireNonNull(originId, "originId");
    }
    public Player player() { return player; }
    public String text() { return text; }
    public String originId() { return originId; }
}
