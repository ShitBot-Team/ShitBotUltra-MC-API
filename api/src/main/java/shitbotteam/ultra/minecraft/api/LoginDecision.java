package shitbotteam.ultra.minecraft.api;

import java.time.Instant;

/** A refusal can include a one-time code; never log the code. */
public final class LoginDecision {
    private final boolean allowed;
    private final String reason;
    private final String code;
    private final Instant expiresAt;
    public LoginDecision(boolean allowed, String reason, String code, Instant expiresAt) {
        this.allowed = allowed;
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
        this.code = java.util.Objects.requireNonNull(code, "code");
        this.expiresAt = expiresAt;
    }
    public boolean allowed() { return allowed; }
    public String reason() { return reason; }
    public String code() { return code; }
    public Instant expiresAt() { return expiresAt; }
}
