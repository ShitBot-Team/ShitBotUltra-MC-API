package shitbotteam.ultra.minecraft.api;

/** Schedules an authoritative snapshot without blocking the calling client worker. */
@FunctionalInterface
public interface SnapshotProvider {
    void capture(SnapshotSink sink);
}
