package shitbotteam.ultra.minecraft.api;

import java.util.Arrays;

/**
 * Explicit platform assertion that an action was rejected BEFORE any side effect.
 * Never use this after partial/unknown execution; ordinary failures become RESULT_UNKNOWN.
 */
public final class ActionRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    public ActionRejectedException(String code) {
        super("Game action rejected before execution");
        if (!Arrays.asList("FORBIDDEN", "INVALID_INPUT", "NOT_FOUND", "BUSY", "UNSUPPORTED_CAPABILITY", "DEADLINE_EXCEEDED", "CANCELED").contains(code)) {
            throw new IllegalArgumentException("Unsupported pre-execution rejection code");
        }
        this.code = code;
    }
    public String code() { return code; }
}
