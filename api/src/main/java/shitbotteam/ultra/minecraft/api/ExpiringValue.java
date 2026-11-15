package shitbotteam.ultra.minecraft.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** One immutable, expiring snapshot for synchronous placeholders. Never performs network I/O. */
public final class ExpiringValue<T> {
    private static final class Entry<T> {
        final T value;
        final long deadline;
        Entry(T value, long deadline) { this.value = value; this.deadline = deadline; }
    }
    private final AtomicReference<Entry<T>> current = new AtomicReference<Entry<T>>();
    /** Publish immutable application data after a separate asynchronous refresh. */
    public void update(T value, Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.compareTo(Duration.ZERO) <= 0 || ttl.compareTo(Duration.ofMinutes(5)) > 0) throw new IllegalArgumentException("Cache TTL must be positive and at most five minutes");
        current.set(new Entry<T>(Objects.requireNonNull(value, "value"), System.nanoTime() + ttl.toNanos()));
    }
    public Optional<T> get() {
        Entry<T> entry = current.get();
        if (entry == null || System.nanoTime() - entry.deadline >= 0) return Optional.empty();
        return Optional.of(entry.value);
    }
    public void invalidate() { current.set(null); }
}
