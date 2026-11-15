package shitbotteam.ultra.minecraft.api;

import java.util.Collection;

/**
 * Complete once on the same serialized platform scheduler that publishes join/leave events.
 * Invoke submit while the snapshot and event stream are still ordered; do not defer completion.
 */
public interface SnapshotSink {
    boolean isCancelled();
    void submit(Collection<Player> players);
    void fail();
}
