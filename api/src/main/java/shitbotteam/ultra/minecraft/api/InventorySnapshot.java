package shitbotteam.ultra.minecraft.api;

import java.time.Instant;

/** Format version 1; data must be a JSON object and at most 2 MiB. */
public final class InventorySnapshot {
    private final Player player;
    private final Instant capturedAt;
    private final JsonData data;
    public InventorySnapshot(Player player, Instant capturedAt, JsonData data) {
        java.util.Objects.requireNonNull(capturedAt, "capturedAt");
        this.player = java.util.Objects.requireNonNull(player, "player");
        this.capturedAt = capturedAt;
        this.data = java.util.Objects.requireNonNull(data, "data");
    }
    public Player player() { return player; }
    public Instant capturedAt() { return capturedAt; }
    public JsonData data() { return data; }
}
