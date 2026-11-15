package shitbotteam.ultra.minecraft.api;

/** Immutable JSON source. The client validates syntax, depth and object shape at the wire boundary. */
public final class JsonData {
    private final String source;
    private JsonData(String source) { this.source = source; }
    public static JsonData of(String source) {
        Protocol.text(source, "JSON", Protocol.MAX_JSON_BYTES);
        if (source.trim().isEmpty()) throw new IllegalArgumentException("JSON is empty");
        return new JsonData(source);
    }
    public static JsonData emptyObject() { return new JsonData("{}"); }
    public static JsonData nullValue() { return new JsonData("null"); }
    public String source() { return source; }
    // Do not implicitly include inventory, chat or other private data in logs.
    @Override public String toString() { return "JsonData[redacted]"; }
}
