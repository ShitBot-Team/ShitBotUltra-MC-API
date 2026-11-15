package shitbotteam.ultra.minecraft.api;

import java.time.Duration;

/** Check on the actual platform thread immediately before performing a side effect. */
public interface CancellationToken {
    boolean isCancelled();
    Duration remaining();
}
