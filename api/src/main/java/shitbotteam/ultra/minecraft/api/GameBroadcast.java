package shitbotteam.ultra.minecraft.api;

public final class GameBroadcast {
    private final String text;
    private final String originId;
    public GameBroadcast(String text, String originId) {
        Protocol.text(text, "broadcast", 16000);
        Protocol.id(originId, "origin ID");
        this.text = java.util.Objects.requireNonNull(text, "text");
        this.originId = java.util.Objects.requireNonNull(originId, "originId");
    }
    public String text() { return text; }
    public String originId() { return originId; }
}
