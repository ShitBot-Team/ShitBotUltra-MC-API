package shitbotteam.ultra.minecraft.api;

/** Stable local or remote failure. A canceled future does not withdraw a sent operation. */
public final class BridgeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    public BridgeException(String code, String message) {
        super(Protocol.text(message, "error message", 4096));
        this.code = Protocol.id(code, "error code");
    }
    public String code() { return code; }
    public boolean resultUnknown() { return "RESULT_UNKNOWN".equals(code); }
}
