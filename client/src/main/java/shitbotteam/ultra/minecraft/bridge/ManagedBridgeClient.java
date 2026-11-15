package shitbotteam.ultra.minecraft.bridge;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.function.Function;
import okhttp3.WebSocket;
import shitbotteam.ultra.minecraft.api.*;

/** Managed Bridge v1 client. All connection/protocol state is confined to one bounded event loop. */
public final class ManagedBridgeClient implements BridgeClient {
    private static final int MAX_PENDING = 64;
    private static final long MAX_BUFFER_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TRANSPORT_BYTES = 6L * 1024 * 1024;
    private final ClientOptions options;
    private final String bootId = UUID.randomUUID().toString();
    private final AtomicBoolean started = new AtomicBoolean(), stopping = new AtomicBoolean();
    private final AtomicBoolean connectDue = new AtomicBoolean(), readyScheduled = new AtomicBoolean();
    private final Object admission = new Object();
    private final AtomicLong receivedBytes = new AtomicLong(), queuedBytes = new AtomicLong();
    private final ArrayBlockingQueue<Runnable> tasks = new ArrayBlockingQueue<Runnable>(256);
    private final Semaphore wake = new Semaphore(0), operations = new Semaphore(128);
    private final Thread loop = ClientThreads.factory("bridge").newThread(this::run);
    private final ScheduledThreadPoolExecutor timers = new ScheduledThreadPoolExecutor(1, ClientThreads.factory("deadlines"));
    private final ThreadPoolExecutor completions = ClientThreads.pool("callbacks", 2, 256);
    private final ThreadPoolExecutor diagnostics = ClientThreads.pool("status", 1, 32);
    private final ThreadPoolExecutor snapshots = ClientThreads.pool("snapshots", 1, 2);
    private final CopyOnWriteArrayList<Consumer<ConnectionStatus>> listeners = new CopyOnWriteArrayList<Consumer<ConnectionStatus>>();
    private final CompletableFuture<ConnectionStatus> ready = new CompletableFuture<ConnectionStatus>();
    private final CompletableFuture<Void> closed = new CompletableFuture<Void>();
    private volatile ConnectionStatus status = new ConnectionStatus(ConnectionState.NEW, "", 0, null, "");
    private volatile Attempt current;
    private SocketTransport transport;
    private ActionDispatcher actions;
    private long nextEpoch, sequence, lastAck, highestEventSequence;
    private int reconnectAttempt;
    private boolean failed;
    private String failureCode = "";
    private ScheduledFuture<?> reconnect;
    private final Map<String, Call> calls = new LinkedHashMap<String, Call>();
    private final NavigableMap<Long, Event> sent = new TreeMap<Long, Event>();
    private final Deque<Event> buffered = new ArrayDeque<Event>();
    private final List<Event> coveredBySnapshot = new ArrayList<Event>();
    private int eventCount;
    private long callBytes, eventBytes;

    private static final class Loss {
        final String code;
        final boolean permanent;
        Loss(String code, boolean permanent) { this.code = code; this.permanent = permanent; }
    }
    private static final class Attempt {
        final long epoch;
        final AtomicReference<Loss> loss = new AtomicReference<Loss>();
        volatile WebSocket socket;
        long incoming;
        boolean snapshotSubmitted;
        ScheduledFuture<?> timer;
        Attempt(long epoch) { this.epoch = epoch; }
    }
    private static final class Call {
        final CompletableFuture<JsonData> future;
        final String id, type;
        final int bytes;
        ScheduledFuture<?> timer;
        Call(CompletableFuture<JsonData> future, String id, String type, int bytes) {
            this.future = future; this.id = id; this.type = type; this.bytes = bytes;
        }
    }
    private static final class Event {
        final CompletableFuture<EventReceipt> future; // null for the internal recovery snapshot
        final String type;
        final JsonElement payload;
        final int bytes;
        final boolean recovery;
        long sequence;
        ScheduledFuture<?> timer;
        boolean finished;
        Event(CompletableFuture<EventReceipt> future, String type, JsonElement payload, int bytes, boolean recovery) {
            this.future = future; this.type = type; this.payload = payload; this.bytes = bytes; this.recovery = recovery;
        }
        boolean membership() { return type.equals("player.joined") || type.equals("player.left") || type.equals("players.snapshot"); }
    }

    public ManagedBridgeClient(ClientOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        timers.setRemoveOnCancelPolicy(true);
        timers.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        timers.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    @Override public CompletableFuture<ConnectionStatus> start() {
        if (started.compareAndSet(false, true)) loop.start();
        return ready;
    }
    @Override public ConnectionStatus status() { return status; }
    @Override public CompletableFuture<Void> whenClosed() { return closed; }
    @Override public Subscription onStatus(Consumer<ConnectionStatus> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (listeners) {
            if (stopping.get()) throw new IllegalStateException("Client is closed");
            if (listeners.size() >= 32) throw new IllegalStateException("At most 32 status subscribers");
            listeners.add(listener);
        }
        return () -> listeners.remove(listener);
    }
    @Override public void close() {
        synchronized (admission) { stopping.set(true); }
        Attempt a = current;
        if (a != null && a.socket != null) a.socket.cancel();
        if (started.compareAndSet(false, true)) loop.start();
        wake.release();
    }

    private void run() {
        try {
            if (!stopping.get()) {
                transport = new SocketTransport();
                actions = new ActionDispatcher(options, timers, new ActionDispatcher.Host() {
                    @Override public boolean post(Runnable action) { return ManagedBridgeClient.this.post(action); }
                    @Override public void reply(long epoch, String type, String id, JsonElement value, String code) { replyAction(epoch, type, id, value, code); }
                });
                connect();
            }
            while (!stopping.get()) {
                wake.acquire();
                if (stopping.get()) break;
                if (connectDue.getAndSet(false) && current == null) connect();
                Attempt a = current;
                if (a != null) {
                    Loss loss = a.loss.getAndSet(null);
                    if (loss != null) disconnect(a, loss.code, loss.permanent);
                }
                if (stopping.get()) break;
                Runnable task = tasks.poll();
                if (task != null) {
                    try { task.run(); }
                    catch (RuntimeException invalid) {
                        Attempt active = current;
                        if (active != null) disconnect(active, "INVALID_RESPONSE", true);
                        else fail("INTERNAL_ERROR");
                    }
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable fatal) {
            failed = true; failureCode = "INTERNAL_ERROR";
        } finally {
            synchronized (admission) { stopping.set(true); }
            Attempt a = current; current = null;
            if (a != null && a.socket != null) a.socket.cancel();
            if (reconnect != null) reconnect.cancel(false);
            if (actions != null) actions.close();
            timers.shutdownNow(); snapshots.shutdownNow();
            if (transport != null) transport.close();
            failOutstanding("CLIENT_CLOSED");
            // Tasks accepted before close must settle their futures too. They see
            // stopping=true and cannot reconnect or write to a later socket.
            Runnable task;
            while ((task = tasks.poll()) != null) {
                try { task.run(); } catch (RuntimeException ignored) { }
            }
            failOutstanding("CLIENT_CLOSED");
            change(failed ? ConnectionState.FAILED : ConnectionState.CLOSED, "", null, failed ? failureCode : "");
            if (readyScheduled.compareAndSet(false, true)) notifyTask(() -> ready.completeExceptionally(error(failed ? failureCode : "CLIENT_CLOSED")));
            notifyTask(() -> closed.complete(null));
            listeners.clear(); diagnostics.shutdownNow(); completions.shutdown();
        }
    }

    private boolean post(Runnable task) {
        synchronized (admission) {
            if (stopping.get()) return false;
            if (!tasks.offer(task)) {
                Attempt a = current;
                if (a != null) lost(a, "BUSY", false);
                return false;
            }
            wake.release(); return true;
        }
    }
    private void lost(Attempt a, String code, boolean permanent) {
        if (a != current || stopping.get()) return;
        a.loss.compareAndSet(null, new Loss(code, permanent));
        if (a.socket != null) a.socket.cancel();
        wake.release();
    }
    private void connect() {
        if (stopping.get()) return;
        Attempt a = new Attempt(++nextEpoch); current = a;
        change(ConnectionState.CONNECTING, "", null, "");
        a.timer = timers.schedule(() -> lost(a, "DEADLINE_EXCEEDED", false), 10, TimeUnit.SECONDS);
        try {
            a.socket = transport.connect(options, new SocketTransport.Listener() {
                @Override public void opened(WebSocket socket) {
                    a.socket = socket;
                    if (!post(() -> ManagedBridgeClient.this.opened(a))) socket.cancel();
                }
                @Override public void text(String message) {
                    int size = message.getBytes(StandardCharsets.UTF_8).length;
                    if (receivedBytes.addAndGet(size) > MAX_BUFFER_BYTES) { receivedBytes.addAndGet(-size); lost(a, "BUSY", false); return; }
                    if (!post(() -> {
                        try { if (!stopping.get() && a == current) receive(a, message); }
                        finally { receivedBytes.addAndGet(-size); }
                    })) receivedBytes.addAndGet(-size);
                }
                @Override public void lost(String code, boolean permanent) { ManagedBridgeClient.this.lost(a, code, permanent); }
            });
        } catch (RuntimeException invalidCredential) { disconnect(a, "INVALID_CREDENTIAL_OR_OPTIONS", true); }
    }
    private void opened(Attempt a) {
        if (stopping.get() || a != current) { if (a.socket != null) a.socket.cancel(); return; }
        change(ConnectionState.HANDSHAKING, "", null, "");
        JsonObject hello = new JsonObject();
        hello.addProperty("protocol_version", Protocol.VERSION); hello.addProperty("adapter_version", options.adapterVersion);
        hello.addProperty("node_id", options.nodeId); hello.addProperty("network_id", options.networkId); hello.addProperty("server_id", options.serverId);
        hello.addProperty("platform", options.platform); hello.addProperty("minecraft_version", options.minecraftVersion); hello.addProperty("boot_id", bootId);
        JsonArray capabilities = new JsonArray();
        for (String capability : options.actions.keySet()) capabilities.add(capability);
        hello.add("capabilities", capabilities);
        send(a, Wire.envelope(options, bootId, 0, "control", "hello", "", hello));
    }
    private void receive(Attempt a, String source) {
        JsonObject frame = Wire.object(Wire.parse(source, Protocol.MAX_MESSAGE_BYTES));
        Wire.integer(frame, "protocol_version", Protocol.VERSION, Protocol.VERSION);
        if (!Wire.string(frame, "node_id").equals(options.nodeId) || !Wire.string(frame, "boot_id").equals(bootId)) throw Wire.invalid();
        Protocol.id(Wire.string(frame, "message_id"), "message ID");
        Wire.instant(frame, "occurred_at");
        long seq = Wire.integer(frame, "sequence", 1, Protocol.MAX_SEQUENCE);
        if (seq <= a.incoming) throw Wire.invalid();
        a.incoming = seq;
        String kind = Wire.string(frame, "kind"), type = Protocol.id(Wire.string(frame, "type"), "message type");
        if (status.state() == ConnectionState.HANDSHAKING) {
            if (!kind.equals("control") || !type.equals("welcome")) throw Wire.invalid();
            welcome(a, Wire.object(frame.get("payload"))); return;
        }
        if (kind.equals("control")) {
            if (type.equals("ack")) acknowledge(a, Wire.object(frame.get("payload")));
            else if (type.equals("snapshot.required")) disconnect(a, "SNAPSHOT_REQUIRED", false);
            else if (type.equals("event.rejected")) disconnect(a, "EVENT_REJECTED", true);
            else throw Wire.invalid();
        } else if (kind.equals("response")) response(frame);
        else if (kind.equals("request")) actions.receive(a.epoch, frame);
        else throw Wire.invalid();
    }
    private void welcome(Attempt a, JsonObject value) {
        Wire.integer(value, "protocol_version", 1, 1);
        Wire.integer(value, "heartbeat_seconds", 20, 20);
        Wire.integer(value, "max_message_bytes", Protocol.MAX_MESSAGE_BYTES, Protocol.MAX_MESSAGE_BYTES);
        Wire.instant(value, "server_time");
        String sessionId = Protocol.id(Wire.string(value, "session_id"), "session ID");
        sequence = Math.max(sequence, Wire.integer(value, "resume_sequence", 0, Protocol.MAX_SEQUENCE));
        lastAck = 0; highestEventSequence = 0;
        if (a.timer != null) a.timer.cancel(false);
        if (!Wire.bool(value, "snapshot_required")) { becameReady(sessionId); return; }
        if (options.snapshots == null) { disconnect(a, "SNAPSHOT_PROVIDER_REQUIRED", true); return; }
        change(ConnectionState.SYNCHRONIZING, sessionId, null, "");
        a.timer = timers.schedule(() -> lost(a, "SNAPSHOT_TIMEOUT", false), 15, TimeUnit.SECONDS);
        AtomicBoolean completed = new AtomicBoolean();
        SnapshotSink sink = new SnapshotSink() {
            @Override public boolean isCancelled() { return stopping.get() || current != a || completed.get(); }
            @Override public void submit(Collection<Player> players) {
                if (isCancelled() || !completed.compareAndSet(false, true)) return;
                try {
                    for (Player p : players) checkPlayer(p);
                    JsonElement data = Wire.object(Payloads.players(players));
                    if (!post(() -> submitSnapshot(a, data))) lost(a, "BUSY", false);
                } catch (RuntimeException invalid) { lost(a, "INVALID_SNAPSHOT", true); }
            }
            @Override public void fail() { if (completed.compareAndSet(false, true)) lost(a, "SNAPSHOT_FAILED", false); }
        };
        try {
            snapshots.execute(() -> {
                if (sink.isCancelled()) return;
                try { options.snapshots.capture(sink); } catch (Throwable failure) { sink.fail(); }
            });
        } catch (RejectedExecutionException busy) { lost(a, "BUSY", false); }
    }

    private void submitSnapshot(Attempt a, JsonElement payload) {
        if (stopping.get() || a != current || status.state() != ConnectionState.SYNCHRONIZING || a.snapshotSubmitted) return;
        a.snapshotSubmitted = true;
        for (Iterator<Event> it = buffered.iterator(); it.hasNext();) {
            Event e = it.next();
            if (e.membership()) { it.remove(); coveredBySnapshot.add(e); }
        }
        Event snapshot = new Event(null, "players.snapshot", payload, 0, true);
        transmitEvent(a, snapshot);
        for (Event e : coveredBySnapshot) e.sequence = snapshot.sequence;
    }
    private void acknowledge(Attempt a, JsonObject value) {
        long ack = Wire.integer(value, "sequence", 1, Protocol.MAX_SEQUENCE);
        if (ack == lastAck) return;
        if (ack < lastAck || ack > highestEventSequence || !sent.containsKey(ack)) throw Wire.invalid();
        lastAck = ack;
        List<Event> done = new ArrayList<Event>(sent.headMap(ack, true).values());
        sent.headMap(ack, true).clear();
        for (Event event : done) {
            if (event.recovery) {
                if (a.timer != null) a.timer.cancel(false);
                for (Event covered : coveredBySnapshot) finishEvent(covered, ack, null);
                coveredBySnapshot.clear();
                becameReady(status.sessionId());
            } else finishEvent(event, ack, null);
        }
        if (status.state() == ConnectionState.READY) {
            while (!buffered.isEmpty() && a == current && !stopping.get()) {
                Event e = buffered.removeFirst();
                if (!e.finished) transmitEvent(a, e);
            }
        }
    }
    private void becameReady(String sessionId) {
        reconnectAttempt = 0;
        change(ConnectionState.READY, sessionId, null, "");
        ConnectionStatus initial = status;
        if (readyScheduled.compareAndSet(false, true)) notifyTask(() -> ready.complete(initial));
    }

    @Override public CompletableFuture<JsonData> request(String type, JsonData payload, Duration timeout) {
        Protocol.id(type, "request type");
        long millis = timeoutMillis(timeout);
        JsonElement body = Wire.object(payload);
        int bytes = payload.source().getBytes(StandardCharsets.UTF_8).length;
        long expires = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        CompletableFuture<JsonData> future = operation();
        if (future.isDone()) return future;
        Attempt submittedOn = current;
        if (!reserveQueued(bytes)) { rejectSubmission(future, "BUSY"); return future; }
        if (!post(() -> {
            queuedBytes.addAndGet(-bytes);
            if (stopping.get() || submittedOn == null || submittedOn != current || status.state() != ConnectionState.READY) { settle(future, null, error("NODE_OFFLINE")); return; }
            long remaining = TimeUnit.NANOSECONDS.toMillis(expires - System.nanoTime());
            if (remaining < 1 || future.isCancelled()) { settle(future, null, error("DEADLINE_EXCEEDED")); return; }
            if (calls.size() >= MAX_PENDING || callBytes + bytes > MAX_BUFFER_BYTES) { settle(future, null, error("BUSY")); return; }
            Attempt a = current;
            String id = UUID.randomUUID().toString();
            Call call = new Call(future, id, type, bytes);
            calls.put(id, call); callBytes += bytes;
            JsonObject frame = nextFrame("request", type, id, body);
            frame.addProperty("timeout_ms", remaining); frame.addProperty("deadline", Instant.now().plusMillis(remaining).toString());
            call.timer = timers.schedule(() -> post(() -> finishCall(id, null, error("RESULT_UNKNOWN"))), remaining, TimeUnit.MILLISECONDS);
            if (!send(a, frame)) finishCall(id, null, error("RESULT_UNKNOWN"));
        })) { queuedBytes.addAndGet(-bytes); rejectSubmission(future, stopping.get() ? "CLIENT_CLOSED" : "BUSY"); }
        return future;
    }
    private void response(JsonObject frame) {
        String id = Protocol.id(Wire.string(frame, "request_id"), "request ID");
        Call call = calls.get(id);
        if (call == null) return;
        if (!Wire.string(frame, "type").equals(call.type)) { finishCall(id, null, error("RESULT_UNKNOWN")); return; }
        if (frame.has("error")) {
            JsonObject remote = Wire.object(frame.get("error"));
            BridgeException err = new BridgeException(Wire.string(remote, "code"), Wire.string(remote, "message"));
            finishCall(id, null, err);
        } else finishCall(id, Wire.data(frame.get("payload")), null);
    }
    private void finishCall(String id, JsonData data, BridgeException error) {
        Call call = calls.remove(id);
        if (call == null) return;
        callBytes -= call.bytes;
        if (call.timer != null) call.timer.cancel(false);
        settle(call.future, data, error);
    }

    private CompletableFuture<EventReceipt> event(String type, JsonData payload) {
        JsonElement body = Wire.object(payload);
        int bytes = payload.source().getBytes(StandardCharsets.UTF_8).length;
        CompletableFuture<EventReceipt> future = operation();
        if (future.isDone()) return future;
        Attempt submittedOn = current;
        if (!reserveQueued(bytes)) { rejectSubmission(future, "BUSY"); return future; }
        if (!post(() -> {
            queuedBytes.addAndGet(-bytes);
            ConnectionState state = status.state();
            if (stopping.get() || submittedOn == null || submittedOn != current || (state != ConnectionState.READY && state != ConnectionState.SYNCHRONIZING)) { settle(future, null, error("NODE_OFFLINE")); return; }
            if (future.isCancelled()) { settle(future, null, error("CANCELED")); return; }
            if (eventCount >= MAX_PENDING || eventBytes + bytes > MAX_BUFFER_BYTES) { settle(future, null, error("BUSY")); return; }
            Event e = new Event(future, type, body, bytes, false); eventCount++; eventBytes += bytes;
            e.timer = timers.schedule(() -> post(() -> expireEvent(e)), 15, TimeUnit.SECONDS);
            if (state == ConnectionState.SYNCHRONIZING) buffered.addLast(e);
            else transmitEvent(current, e);
        })) { queuedBytes.addAndGet(-bytes); rejectSubmission(future, stopping.get() ? "CLIENT_CLOSED" : "BUSY"); }
        return future;
    }
    private void transmitEvent(Attempt a, Event e) {
        if (sequence >= Protocol.MAX_SEQUENCE) { finishEvent(e, 0, error("SESSION_EXPIRED")); fail("SESSION_EXPIRED"); return; }
        JsonObject frame = nextFrame("event", e.type, "", e.payload);
        e.sequence = sequence; highestEventSequence = sequence; sent.put(e.sequence, e);
        if (!send(a, frame)) disconnect(a, "NODE_OFFLINE", false);
    }
    private void expireEvent(Event e) {
        if (e.finished) return;
        buffered.remove(e); coveredBySnapshot.remove(e);
        finishEvent(e, 0, error(e.sequence == 0 ? "DEADLINE_EXCEEDED" : "RESULT_UNKNOWN"));
        if (e.sequence != 0 && current != null) disconnect(current, "ACK_TIMEOUT", false);
    }
    private void finishEvent(Event e, long ack, BridgeException error) {
        if (e.finished || e.recovery) return;
        e.finished = true; eventCount--; eventBytes -= e.bytes;
        if (e.timer != null) e.timer.cancel(false);
        settle(e.future, error == null ? new EventReceipt(ack) : null, error);
    }

    @Override public CompletableFuture<LoginDecision> authorize(Player player, Duration timeout) {
        checkPlayer(player);
        return map(request("login.authorize", Payloads.playerRequest(player), timeout), Payloads::login);
    }
    @Override public CompletableFuture<BindingStatus> binding(String name, Duration timeout) {
        new Player(name, "", IdentityMode.OFFLINE);
        JsonObject request = new JsonObject(); request.addProperty("name", name);
        return map(request("binding.get", Wire.data(request), timeout), Payloads::binding);
    }
    @Override public CompletableFuture<EventReceipt> chat(ChatMessage message) { checkPlayer(message.player()); return event("chat.message", Payloads.chat(message)); }
    @Override public CompletableFuture<EventReceipt> joined(Player player) { checkPlayer(player); return event("player.joined", Payloads.playerRequest(player)); }
    @Override public CompletableFuture<EventReceipt> left(Player player) { checkPlayer(player); return event("player.left", Payloads.playerRequest(player)); }
    @Override public CompletableFuture<EventReceipt> players(Collection<Player> players) {
        for (Player player : players) checkPlayer(player);
        return event("players.snapshot", Payloads.players(players));
    }
    @Override public CompletableFuture<EventReceipt> inventory(InventorySnapshot snapshot) { checkPlayer(snapshot.player()); return event("inventory.snapshot", Payloads.inventory(snapshot)); }
    @Override public CompletableFuture<EventReceipt> serverReady() { return event("server.ready", JsonData.emptyObject()); }
    private void checkPlayer(Player player) {
        if (player.identityMode() != options.identityMode) throw new IllegalArgumentException("Player mode differs from the configured network");
    }

    private JsonObject nextFrame(String kind, String type, String requestId, JsonElement payload) {
        if (sequence >= Protocol.MAX_SEQUENCE) { fail("SESSION_EXPIRED"); throw error("SESSION_EXPIRED"); }
        return Wire.envelope(options, bootId, ++sequence, kind, type, requestId, payload);
    }
    private boolean send(Attempt a, JsonObject frame) {
        if (stopping.get() || a == null || a != current || a.socket == null) return false;
        String text = frame.toString();
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > Protocol.MAX_MESSAGE_BYTES) throw error("INVALID_INPUT");
        if (a.socket.queueSize() + bytes > MAX_TRANSPORT_BYTES || !a.socket.send(text)) { lost(a, "BUSY", false); return false; }
        return true;
    }
    private void replyAction(long epoch, String type, String id, JsonElement payload, String code) {
        Attempt a = current;
        if (stopping.get() || a == null || a.epoch != epoch) return;
        if (sequence >= Protocol.MAX_SEQUENCE) { fail("SESSION_EXPIRED"); return; }
        JsonObject frame = nextFrame("response", type, id, code == null ? payload : null);
        if (code != null) {
            JsonObject err = new JsonObject(); err.addProperty("code", code); err.addProperty("message", "Game action was not confirmed: " + code); frame.add("error", err);
        }
        if (!send(a, frame)) disconnect(a, "NODE_OFFLINE", false);
    }
    private void disconnect(Attempt a, String code, boolean permanent) {
        if (a == null || a != current) return;
        current = null;
        if (a.socket != null) a.socket.cancel();
        if (a.timer != null) a.timer.cancel(false);
        if (actions != null) actions.disconnected(a.epoch);
        failOutstanding("RESULT_UNKNOWN");
        if (permanent) { fail(code); return; }
        if (stopping.get()) return;
        reconnectAttempt = Math.min(reconnectAttempt + 1, 20);
        long base = Math.min(30000, 1000L << Math.min(reconnectAttempt - 1, 5));
        long delay = base + ThreadLocalRandom.current().nextLong(Math.max(1, base / 4));
        change(ConnectionState.BACKOFF, "", Instant.now().plusMillis(delay), code);
        reconnect = timers.schedule(() -> { connectDue.set(true); wake.release(); }, delay, TimeUnit.MILLISECONDS);
    }
    private void fail(String code) {
        if (!failed) { failed = true; failureCode = code; }
        synchronized (admission) { stopping.set(true); }
        wake.release();
    }
    private void failOutstanding(String code) {
        for (String id : new ArrayList<String>(calls.keySet())) finishCall(id, null, error(code));
        Set<Event> all = new LinkedHashSet<Event>(); all.addAll(sent.values()); all.addAll(buffered); all.addAll(coveredBySnapshot);
        sent.clear(); buffered.clear(); coveredBySnapshot.clear();
        for (Event event : all) finishEvent(event, 0, error(event.sequence == 0 ? "NODE_OFFLINE" : code));
    }
    private void change(ConnectionState state, String sessionId, Instant retryAt, String code) {
        ConnectionStatus next = new ConnectionStatus(state, sessionId, reconnectAttempt, retryAt, code); status = next;
        if (listeners.isEmpty()) return;
        // Diagnostics are best-effort; slow listeners cannot block protocol I/O.
        try { diagnostics.execute(() -> { for (Consumer<ConnectionStatus> listener : listeners) { try { listener.accept(next); } catch (Throwable ignored) { } } }); }
        catch (RejectedExecutionException ignored) { }
    }
    private <T> CompletableFuture<T> operation() {
        CompletableFuture<T> result = new CompletableFuture<T>();
        if (!started.get() || stopping.get() || !operations.tryAcquire()) result.completeExceptionally(error(stopping.get() ? "CLIENT_CLOSED" : !started.get() ? "NODE_OFFLINE" : "BUSY"));
        return result;
    }
    private <T> void settle(CompletableFuture<T> future, T value, BridgeException error) {
        notifyTask(() -> {
            try { if (error == null) future.complete(value); else future.completeExceptionally(error); }
            finally { operations.release(); }
        });
    }
    private boolean reserveQueued(int bytes) {
        if (queuedBytes.addAndGet(bytes) <= MAX_BUFFER_BYTES) return true;
        queuedBytes.addAndGet(-bytes); return false;
    }
    // Rejection occurs before this newly created future is returned to its caller,
    // so no user continuation can run inline here or race executor shutdown.
    private <T> void rejectSubmission(CompletableFuture<T> future, String code) {
        future.completeExceptionally(error(code)); operations.release();
    }
    private void notifyTask(Runnable task) { completions.execute(task); }
    private static BridgeException error(String code) { return new BridgeException(code, "Bridge operation did not complete: " + code); }
    private static long timeoutMillis(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("Timeout must be 1 ms to 30 seconds");
        return timeout.toMillis();
    }
    private static <A, B> CompletableFuture<B> map(CompletableFuture<A> source, Function<A, B> mapper) {
        CompletableFuture<B> result = source.thenApply(mapper);
        result.whenComplete((value, error) -> { if (result.isCancelled()) source.cancel(false); });
        return result;
    }
}
