package shitbotteam.ultra.minecraft.api;

import java.time.Instant;

/** TPS is sampled by the game platform; null means unavailable. */
public final class ServerPerformance {
    private final Instant measuredAt;
    private final Double tps;
    private final Double tps5Minutes;
    private final Double tps15Minutes;
    private final Double mspt;
    private final int onlinePlayers;
    private final int maxPlayers;
    private final long usedMemoryBytes;
    private final long maxMemoryBytes;
    public ServerPerformance(Instant measuredAt, Double tps, Double tps5Minutes, Double tps15Minutes, Double mspt, int onlinePlayers, int maxPlayers, long usedMemoryBytes, long maxMemoryBytes) {
        java.util.Objects.requireNonNull(measuredAt, "measuredAt");
        for (Double v : new Double[]{tps, tps5Minutes, tps15Minutes, mspt}) if (v != null && (!Double.isFinite(v) || v < 0)) throw new IllegalArgumentException("Invalid game metric");
        if (onlinePlayers < 0 || maxPlayers < 0 || usedMemoryBytes < 0 || maxMemoryBytes < 0) throw new IllegalArgumentException("Negative game metric");
        this.measuredAt = measuredAt;
        this.tps = tps;
        this.tps5Minutes = tps5Minutes;
        this.tps15Minutes = tps15Minutes;
        this.mspt = mspt;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.usedMemoryBytes = usedMemoryBytes;
        this.maxMemoryBytes = maxMemoryBytes;
    }
    public Instant measuredAt() { return measuredAt; }
    public Double tps() { return tps; }
    public Double tps5Minutes() { return tps5Minutes; }
    public Double tps15Minutes() { return tps15Minutes; }
    public Double mspt() { return mspt; }
    public int onlinePlayers() { return onlinePlayers; }
    public int maxPlayers() { return maxPlayers; }
    public long usedMemoryBytes() { return usedMemoryBytes; }
    public long maxMemoryBytes() { return maxMemoryBytes; }
}
