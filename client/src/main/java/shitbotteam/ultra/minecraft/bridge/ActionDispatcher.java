package shitbotteam.ultra.minecraft.bridge;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import shitbotteam.ultra.minecraft.api.*;

/** All cache mutations run on the connection loop; game handlers run on separate workers. */
final class ActionDispatcher {
    interface Host {
        boolean post(Runnable action);
        void reply(long epoch, String type, String requestId, JsonElement result, String code);
    }
    private static final long RETENTION_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final int MAX_CACHE_BYTES = 8 * 1024 * 1024;
    private final ClientOptions options;
    private final ScheduledThreadPoolExecutor timers;
    private final Host host;
    private final ThreadPoolExecutor handlers = ClientThreads.pool("game-dispatch", 2, 64);
    private final LinkedHashMap<String, Entry> cache = new LinkedHashMap<String, Entry>();
    private final Map<String, String> requestKeys = new HashMap<String, String>();
    private int running, cachedBytes, pendingBytes;

    private static final class Entry implements CancellationToken {
        final String key, type, hash;
        final long epoch, expiresNanos;
        final Instant deadline;
        final Set<String> requestIds = new LinkedHashSet<String>();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean invoked = new AtomicBoolean();
        boolean done;
        JsonElement result;
        String error;
        int bytes;
        long retainedUntil;
        ScheduledFuture<?> timer;
        Runnable dispatch;
        final int inputBytes;
        Entry(String key, String type, String hash, long epoch, long budgetMillis, Instant deadline, int inputBytes) {
            this.key = key; this.type = type; this.hash = hash; this.epoch = epoch; this.deadline = deadline;
            this.inputBytes = inputBytes;
            expiresNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        }
        @Override public boolean isCancelled() { return cancelled.get() || System.nanoTime() - expiresNanos >= 0; }
        @Override public Duration remaining() { return Duration.ofNanos(Math.max(0, expiresNanos - System.nanoTime())); }
    }

    ActionDispatcher(ClientOptions options, ScheduledThreadPoolExecutor timers, Host host) {
        this.options = options; this.timers = timers; this.host = host;
    }

    void receive(long epoch, JsonObject envelope) {
        String requestId = Protocol.id(Wire.string(envelope, "request_id"), "request ID");
        String type = Protocol.id(Wire.string(envelope, "type"), "action type");
        String key = Protocol.id(Wire.string(envelope, "idempotency_key"), "idempotency key");
        long timeout = Wire.integer(envelope, "timeout_ms", 1, 30000);
        Instant deadline = Wire.instant(envelope, "deadline");
        JsonData data = Wire.data(Wire.object(envelope.get("payload")));
        int inputBytes = data.source().getBytes(StandardCharsets.UTF_8).length;
        String hash = digest(data.source());
        clean();
        String priorKey = requestKeys.get(requestId);
        if (priorKey != null && !priorKey.equals(key)) { host.reply(epoch, type, requestId, null, "IDENTITY_CONFLICT"); return; }
        Entry prior = cache.get(key);
        if (prior != null) {
            if (!prior.type.equals(type) || !prior.hash.equals(hash)) { host.reply(epoch, type, requestId, null, "IDENTITY_CONFLICT"); return; }
            if (prior.requestIds.size() >= 8 && !prior.requestIds.contains(requestId)) { host.reply(epoch, type, requestId, null, "BUSY"); return; }
            prior.requestIds.add(requestId); requestKeys.put(requestId, key);
            host.reply(epoch, type, requestId, prior.done ? prior.result : null, prior.done ? prior.error : "RESULT_UNKNOWN");
            return;
        }
        ActionHandler handler = options.actions.get(type);
        if (handler == null) { host.reply(epoch, type, requestId, null, "UNSUPPORTED_CAPABILITY"); return; }
        if (running >= 64 || cache.size() >= 512 || cachedBytes >= MAX_CACHE_BYTES || pendingBytes + inputBytes > MAX_CACHE_BYTES) { host.reply(epoch, type, requestId, null, "BUSY"); return; }
        long budget;
        try { budget = Math.min(timeout, Duration.between(Instant.now(), deadline.plusSeconds(30)).toMillis()); }
        catch (ArithmeticException | java.time.DateTimeException e) { host.reply(epoch, type, requestId, null, "INVALID_INPUT"); return; }
        if (budget <= 0) { host.reply(epoch, type, requestId, null, "DEADLINE_EXCEEDED"); return; }
        validatePayload(type, data);
        Entry entry = new Entry(key, type, hash, epoch, budget, deadline, inputBytes);
        entry.requestIds.add(requestId); requestKeys.put(requestId, key); cache.put(key, entry); running++; pendingBytes += inputBytes;
        entry.timer = timers.schedule(() -> host.post(() -> finish(entry, null, entry.invoked.get() ? "RESULT_UNKNOWN" : "DEADLINE_EXCEEDED")), budget, TimeUnit.MILLISECONDS);
        ActionRequest action = new ActionRequest(type, requestId, key, data, deadline, entry);
        try {
            entry.dispatch = () -> {
                if (entry.isCancelled()) { host.post(() -> finish(entry, null, "DEADLINE_EXCEEDED")); return; }
                entry.invoked.set(true);
                try {
                    CompletionStage<JsonData> result = handler.handle(action);
                    if (result == null) { host.post(() -> finish(entry, null, "RESULT_UNKNOWN")); return; }
                    result.whenComplete((value, error) -> host.post(() -> {
                        if (entry.done) return;
                        if (error != null || value == null) { finish(entry, null, "RESULT_UNKNOWN"); return; }
                        try {
                            JsonElement parsed = Wire.parse(value.source(), Protocol.MAX_JSON_BYTES);
                            validateResult(type, data, value);
                            finish(entry, parsed, null);
                        } catch (RuntimeException invalid) { finish(entry, null, "RESULT_UNKNOWN"); }
                    }));
                } catch (Throwable failure) {
                    // A thrown callback may follow a side effect. Never convert it to a safe retry.
                    host.post(() -> finish(entry, null, "RESULT_UNKNOWN"));
                }
            };
            handlers.execute(entry.dispatch);
        } catch (RejectedExecutionException full) { finish(entry, null, "BUSY"); }
    }

    private void validatePlayer(Player player) {
        if (player.identityMode() != options.identityMode) throw new BridgeException("IDENTITY_CONFLICT", "Game action player mode differs from the configured network");
    }
    private void validatePayload(String type, JsonData data) {
        switch (type) {
            case Capabilities.CHAT_SEND: Payloads.broadcast(data); break;
            case Capabilities.PLAYER_KICK: validatePlayer(Payloads.kick(data).player()); break;
            case Capabilities.COMMAND_EXECUTE: Payloads.command(data); break;
            case Capabilities.INVENTORY_READ: validatePlayer(Payloads.requestedPlayer(data)); break;
            default: break;
        }
    }
    private void validateResult(String type, JsonData request, JsonData data) {
        if (Capabilities.INVENTORY_READ.equals(type)) {
            InventorySnapshot snapshot = Payloads.inventory(data);
            validatePlayer(snapshot.player());
            if (!snapshot.player().equals(Payloads.requestedPlayer(request))) throw Wire.invalid();
        } else if (Capabilities.TPS_READ.equals(type)) Payloads.performance(data);
    }

    private void finish(Entry entry, JsonElement result, String error) {
        if (entry.done) return;
        if (error == null && entry.isCancelled()) error = "RESULT_UNKNOWN";
        int bytes = result == null ? 0 : result.toString().getBytes(StandardCharsets.UTF_8).length;
        if (cachedBytes + bytes > MAX_CACHE_BYTES) { result = null; bytes = 0; error = "RESULT_UNKNOWN"; }
        entry.done = true; entry.cancelled.set(true); entry.retainedUntil = System.nanoTime() + RETENTION_NANOS;
        entry.result = result; entry.error = error; entry.bytes = bytes; cachedBytes += bytes; running--;
        pendingBytes -= entry.inputBytes;
        if (entry.dispatch != null) handlers.remove(entry.dispatch);
        entry.dispatch = null;
        if (entry.timer != null) entry.timer.cancel(false);
        // A duplicate request already received RESULT_UNKNOWN while running. Only
        // reply to the original here; cached duplicates can explicitly query later.
        host.reply(entry.epoch, entry.type, entry.requestIds.iterator().next(), result, error);
    }
    void disconnected(long epoch) {
        for (Entry entry : cache.values()) if (entry.epoch == epoch && !entry.done) finish(entry, null, "RESULT_UNKNOWN");
        handlers.getQueue().clear();
    }
    private void clean() {
        long now = System.nanoTime();
        Iterator<Entry> it = cache.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.done && now - entry.retainedUntil >= 0) {
                cachedBytes -= entry.bytes;
                for (String id : entry.requestIds) requestKeys.remove(id);
                it.remove();
            }
        }
    }
    void close() {
        for (Entry entry : cache.values()) { entry.cancelled.set(true); if (entry.timer != null) entry.timer.cancel(false); }
        handlers.shutdownNow(); cache.clear(); requestKeys.clear();
    }
    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
