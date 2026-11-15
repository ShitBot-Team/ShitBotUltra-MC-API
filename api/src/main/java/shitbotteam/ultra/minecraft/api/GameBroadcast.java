package shitbotteam.ultra.minecraft.api;

public final class GameBroadcast {
    private final String text;
    private final String originId;
    public GameBroadcast(String text, String originId) {
        Protocol.text(text, "broadcast", Protocol.MAX_JSON_BYTES);
        // Core combines connection ID and source message ID in this value.
        Protocol.text(originId, "origin ID", 1024);
        if (originId.isEmpty()) throw new IllegalArgumentException("Missing broadcast origin");
        this.text = java.util.Objects.requireNonNull(text, "text");
        this.originId = java.util.Objects.requireNonNull(originId, "originId");
    }
    public String text() { return text; }
    public String originId() { return originId; }
}
