package shitbotteam.ultra.minecraft.api;

import java.time.Instant;

/** Redacted diagnostic snapshot; no token, chat, bind code or raw network exception. */
public final class ConnectionStatus {
    private final ConnectionState state;
    private final String sessionId;
    private final int attempt;
    private final Instant retryAt;
    private final String errorCode;
    public ConnectionStatus(ConnectionState state, String sessionId, int attempt, Instant retryAt, String errorCode) {
        this.state = java.util.Objects.requireNonNull(state, "state");
        this.sessionId = java.util.Objects.requireNonNull(sessionId, "sessionId");
        this.attempt = attempt;
        this.retryAt = retryAt;
        this.errorCode = java.util.Objects.requireNonNull(errorCode, "errorCode");
    }
    public ConnectionState state() { return state; }
    public String sessionId() { return sessionId; }
    public int attempt() { return attempt; }
    public Instant retryAt() { return retryAt; }
    public String errorCode() { return errorCode; }
}
