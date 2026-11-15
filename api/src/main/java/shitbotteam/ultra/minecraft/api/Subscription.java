package shitbotteam.ultra.minecraft.api;

/** Idempotent removal; callbacks already running may finish. */
public interface Subscription extends AutoCloseable {
    @Override void close();
}
