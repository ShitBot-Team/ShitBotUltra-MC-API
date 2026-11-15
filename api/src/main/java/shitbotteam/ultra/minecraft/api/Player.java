package shitbotteam.ultra.minecraft.api;

import java.util.Locale;
import java.util.Objects;

/** Names are case-sensitive. Online/forwarded identities require a UUID. */
public final class Player {
    private final String name;
    private final String uuid;
    private final IdentityMode identityMode;
    public Player(String name, String uuid, IdentityMode identityMode) {
        Protocol.text(name, "player name", 256);
        Protocol.id(name, "player name");
        if (name.codePointCount(0, name.length()) > 64) throw new IllegalArgumentException("Player name exceeds 64 characters");
        this.identityMode = Objects.requireNonNull(identityMode, "identityMode");
        String normalized = uuid == null ? "" : uuid.toLowerCase(Locale.ROOT);
        if (!normalized.isEmpty() && !normalized.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new IllegalArgumentException("Invalid player UUID");
        if (identityMode != IdentityMode.OFFLINE && normalized.isEmpty()) throw new IllegalArgumentException("Online/forwarded player requires UUID");
        this.name = name;
        this.uuid = normalized;
    }
    public String name() { return name; }
    public String uuid() { return uuid; }
    public IdentityMode identityMode() { return identityMode; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Player)) return false;
        Player p = (Player) other;
        return name.equals(p.name) && uuid.equals(p.uuid) && identityMode == p.identityMode;
    }
    @Override public int hashCode() { return Objects.hash(name, uuid, identityMode); }
}
