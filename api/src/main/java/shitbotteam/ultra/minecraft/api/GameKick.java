package shitbotteam.ultra.minecraft.api;

public final class GameKick {
    private final Player player;
    private final String reason;
    public GameKick(Player player, String reason) {
        Protocol.text(reason, "kick reason", Protocol.MAX_JSON_BYTES);
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }
    public Player player() { return player; }
    public String reason() { return reason; }
}
