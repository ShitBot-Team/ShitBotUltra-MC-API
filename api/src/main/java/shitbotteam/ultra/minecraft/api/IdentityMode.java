package shitbotteam.ultra.minecraft.api;

public enum IdentityMode {
    ONLINE("online"), FORWARDED("forwarded"), OFFLINE("offline");
    private final String wire;
    IdentityMode(String wire) { this.wire = wire; }
    public String wire() { return wire; }
    public static IdentityMode fromWire(String value) {
        for (IdentityMode mode : values()) if (mode.wire.equals(value)) return mode;
        throw new IllegalArgumentException("Unknown player identity mode");
    }
}
