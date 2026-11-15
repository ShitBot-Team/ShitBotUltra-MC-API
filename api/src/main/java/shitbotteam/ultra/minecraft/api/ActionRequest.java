package shitbotteam.ultra.minecraft.api;

import java.time.Instant;

public final class ActionRequest {
    private final String type;
    private final String requestId;
    private final String idempotencyKey;
    private final JsonData payload;
    private final Instant deadline;
    private final CancellationToken cancellation;
    public ActionRequest(String type, String requestId, String idempotencyKey, JsonData payload, Instant deadline, CancellationToken cancellation) {
        Protocol.id(type, "action type");
        Protocol.id(requestId, "request ID");
        Protocol.id(idempotencyKey, "idempotency key");
        java.util.Objects.requireNonNull(deadline, "deadline");
        this.type = java.util.Objects.requireNonNull(type, "type");
        this.requestId = java.util.Objects.requireNonNull(requestId, "requestId");
        this.idempotencyKey = java.util.Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.payload = java.util.Objects.requireNonNull(payload, "payload");
        this.deadline = deadline;
        this.cancellation = java.util.Objects.requireNonNull(cancellation, "cancellation");
    }
    public String type() { return type; }
    public String requestId() { return requestId; }
    public String idempotencyKey() { return idempotencyKey; }
    public JsonData payload() { return payload; }
    public Instant deadline() { return deadline; }
    public CancellationToken cancellation() { return cancellation; }
}
