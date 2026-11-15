package shitbotteam.ultra.minecraft.api;

/** Core acknowledged handling, not delivery of external bot/game side effects. */
public final class EventReceipt {
    private final long sequence;
    public EventReceipt(long sequence) {
        if (sequence < 1 || sequence > Protocol.MAX_SEQUENCE) throw new IllegalArgumentException("Invalid sequence");
        this.sequence = sequence;
    }
    public long sequence() { return sequence; }
}
