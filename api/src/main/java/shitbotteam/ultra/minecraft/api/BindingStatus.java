package shitbotteam.ultra.minecraft.api;

public final class BindingStatus {
    private final Player player;
    private final boolean whitelisted;
    private final boolean bound;
    public BindingStatus(Player player, boolean whitelisted, boolean bound) {
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.whitelisted = whitelisted;
        this.bound = bound;
    }
    public Player player() { return player; }
    public boolean whitelisted() { return whitelisted; }
    public boolean bound() { return bound; }
}
