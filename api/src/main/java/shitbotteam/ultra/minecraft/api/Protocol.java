package shitbotteam.ultra.minecraft.api;

import java.nio.charset.StandardCharsets;

/** Shared Bridge v1 limits; no platform or JSON-library types are exposed. */
public final class Protocol {
    public static final int VERSION = 1;
    public static final long MAX_SEQUENCE = 9007199254740991L;
    public static final int MAX_MESSAGE_BYTES = 3 * 1024 * 1024;
    public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private Protocol() { }

    public static String text(String value, String name, int maxBytes) {
        if (value == null || value.length() > maxBytes) throw new IllegalArgumentException(name + " exceeds its limit");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw new IllegalArgumentException(name + " is not valid Unicode");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException(name + " is not valid Unicode");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw new IllegalArgumentException(name + " exceeds its byte limit");
        return value;
    }

    public static String id(String value, String name) {
        text(value, name, 256);
        if (value.isEmpty() || space(value.codePointAt(0)) || space(value.codePointBefore(value.length()))) throw new IllegalArgumentException(name + " is empty or padded");
        for (int i = 0; i < value.length();) {
            int c = value.codePointAt(i);
            if (Character.isISOControl(c)) throw new IllegalArgumentException(name + " contains a control character");
            i += Character.charCount(c);
        }
        return value;
    }

    public static String localId(String value, String name) {
        id(value, name);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
    private static boolean space(int codePoint) { return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint); }
}
