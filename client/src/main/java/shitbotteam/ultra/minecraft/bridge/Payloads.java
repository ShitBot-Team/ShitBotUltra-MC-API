package shitbotteam.ultra.minecraft.bridge;

import com.google.gson.*;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import shitbotteam.ultra.minecraft.api.*;

/** Typed helpers for platform adapters; public signatures do not expose Gson. */
public final class Payloads {
    private Payloads() { }

    public static JsonData playerRequest(Player player) { return Wire.data(Wire.withPlayer(player)); }
    public static Player requestedPlayer(JsonData payload) { return Wire.player(Wire.object(payload).get("player")); }
    public static JsonData players(Collection<Player> players) {
        if (players == null || players.size() > 10000) throw new IllegalArgumentException("Snapshot has more than 10000 players");
        JsonArray list = new JsonArray();
        Set<String> names = new HashSet<String>();
        for (Player player : players) {
            if (!names.add(player.name())) throw new IllegalArgumentException("Duplicate player name in snapshot");
            list.add(Wire.player(player));
        }
        JsonObject o = new JsonObject(); o.add("players", list); return Wire.data(o);
    }
    public static JsonData chat(ChatMessage message) {
        JsonObject o = Wire.withPlayer(message.player());
        o.addProperty("text", message.text());
        if (!message.originId().isEmpty()) o.addProperty("origin_id", message.originId());
        return Wire.data(o);
    }
    public static JsonData inventory(InventorySnapshot snapshot) {
        JsonObject o = Wire.withPlayer(snapshot.player());
        o.addProperty("captured_at", snapshot.capturedAt().toString());
        o.addProperty("format_version", 1);
        o.add("data", Wire.object(snapshot.data()));
        return Wire.data(o);
    }
    public static InventorySnapshot inventory(JsonData payload) {
        JsonObject o = Wire.object(payload);
        Wire.integer(o, "format_version", 1, 1);
        return new InventorySnapshot(Wire.player(o.get("player")), Wire.instant(o, "captured_at"), Wire.data(Wire.object(o.get("data"))));
    }
    public static GameBroadcast broadcast(JsonData payload) {
        JsonObject o = Wire.object(payload);
        return new GameBroadcast(Wire.string(o, "text"), Wire.string(o, "origin_id"));
    }
    public static GameKick kick(JsonData payload) {
        JsonObject o = Wire.object(payload);
        return new GameKick(Wire.player(o.get("player")), Wire.string(o, "reason"));
    }
    public static GameCommand command(JsonData payload) {
        JsonObject o = Wire.object(payload);
        return new GameCommand(Wire.string(o, "command"), Wire.string(o, "actor_id"));
    }
    public static JsonData performance(ServerPerformance p) {
        JsonObject o = new JsonObject();
        o.addProperty("measured_at", p.measuredAt().toString());
        if (p.tps() == null) o.add("tps", JsonNull.INSTANCE); else o.addProperty("tps", p.tps());
        if (p.tps5Minutes() != null) o.addProperty("tps_5m", p.tps5Minutes());
        if (p.tps15Minutes() != null) o.addProperty("tps_15m", p.tps15Minutes());
        if (p.mspt() != null) o.addProperty("mspt", p.mspt());
        o.addProperty("online_players", p.onlinePlayers()); o.addProperty("max_players", p.maxPlayers());
        o.addProperty("used_memory_bytes", p.usedMemoryBytes()); o.addProperty("max_memory_bytes", p.maxMemoryBytes());
        return Wire.data(o);
    }
    public static ServerPerformance performance(JsonData payload) {
        JsonObject o = Wire.object(payload);
        if (!o.has("tps")) throw Wire.invalid();
        return new ServerPerformance(Wire.instant(o, "measured_at"), metric(o, "tps"), metric(o, "tps_5m"), metric(o, "tps_15m"), metric(o, "mspt"),
            (int) Wire.integer(o, "online_players", 0, Integer.MAX_VALUE), (int) Wire.integer(o, "max_players", 0, Integer.MAX_VALUE),
            o.has("used_memory_bytes") ? Wire.integer(o, "used_memory_bytes", 0, Long.MAX_VALUE) : 0,
            o.has("max_memory_bytes") ? Wire.integer(o, "max_memory_bytes", 0, Long.MAX_VALUE) : 0);
    }
    private static Double metric(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        JsonElement value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw Wire.invalid();
        double n = value.getAsDouble();
        if (!Double.isFinite(n) || n < 0) throw Wire.invalid();
        return n;
    }
    static LoginDecision login(JsonData payload) {
        JsonObject o = Wire.object(payload);
        boolean allowed = Wire.bool(o, "allowed");
        String code = Wire.optionalString(o, "code");
        if (!code.isEmpty() && !code.matches("[0-9]{8}")) throw Wire.invalid();
        Instant expires = o.has("expires_at") ? Wire.instant(o, "expires_at") : null;
        if (!code.isEmpty() && (allowed || expires == null)) throw Wire.invalid();
        return new LoginDecision(allowed, Wire.optionalString(o, "reason"), code, expires);
    }
    static BindingStatus binding(JsonData payload) {
        JsonObject o = Wire.object(payload);
        return new BindingStatus(Wire.player(o.get("player")), Wire.bool(o, "whitelisted"), Wire.bool(o, "bound"));
    }
}
