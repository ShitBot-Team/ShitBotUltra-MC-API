package shitbotteam.ultra.minecraft.bridge;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import shitbotteam.ultra.minecraft.api.*;

/** Explicit codec: no reflection over public DTOs, duplicate keys or permissive Gson parsing. */
final class Wire {
    private Wire() { }

    static JsonElement parse(String source, int maxBytes) {
        Protocol.text(source, "JSON", maxBytes);
        try (JsonReader reader = new JsonReader(new StringReader(source))) {
            reader.setStrictness(Strictness.STRICT);
            reader.setNestingLimit(64);
            JsonElement value = read(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
            return value;
        } catch (IOException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static JsonElement read(JsonReader r, int depth) throws IOException {
        if (depth > 64) throw invalid();
        switch (r.peek()) {
            case BEGIN_OBJECT:
                r.beginObject();
                JsonObject object = new JsonObject();
                while (r.hasNext()) {
                    String name = r.nextName();
                    if (object.has(name)) throw invalid();
                    object.add(name, read(r, depth + 1));
                }
                r.endObject();
                return object;
            case BEGIN_ARRAY:
                r.beginArray();
                JsonArray array = new JsonArray();
                while (r.hasNext()) array.add(read(r, depth + 1));
                r.endArray();
                return array;
            case STRING: return new JsonPrimitive(r.nextString());
            case NUMBER:
                String number = r.nextString();
                if (number.length() > 256) throw invalid();
                return new JsonPrimitive(new BigDecimal(number));
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            case NULL: r.nextNull(); return JsonNull.INSTANCE;
            default: throw invalid();
        }
    }

    static BridgeException invalid() { return new BridgeException("INVALID_RESPONSE", "Bridge JSON or message shape is invalid"); }
    static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid();
        return value.getAsJsonObject();
    }
    static JsonObject object(JsonData value) { return object(parse(value.source(), Protocol.MAX_JSON_BYTES)); }
    static String string(JsonObject o, String key) {
        JsonElement v = o.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) throw invalid();
        return v.getAsString();
    }
    static String optionalString(JsonObject o, String key) { return o.has(key) ? string(o, key) : ""; }
    static long integer(JsonObject o, String key, long min, long max) {
        JsonElement v = o.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw invalid();
        try {
            long n = new BigDecimal(v.getAsString()).longValueExact();
            if (n < min || n > max) throw invalid();
            return n;
        } catch (ArithmeticException | NumberFormatException e) { throw invalid(); }
    }
    static boolean bool(JsonObject o, String key) {
        JsonElement v = o.get(key);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw invalid();
        return v.getAsBoolean();
    }
    static Instant instant(JsonObject o, String key) {
        try { return Instant.parse(string(o, key)); }
        catch (java.time.format.DateTimeParseException e) { throw invalid(); }
    }
    static JsonData data(JsonElement value) {
        if (value == null) throw invalid();
        return JsonData.of(value.toString());
    }
    static JsonObject player(Player value) {
        JsonObject o = new JsonObject();
        o.addProperty("name", value.name());
        if (!value.uuid().isEmpty()) o.addProperty("uuid", value.uuid());
        o.addProperty("identity_mode", value.identityMode().wire());
        return o;
    }
    static Player player(JsonElement value) {
        JsonObject o = object(value);
        return new Player(string(o, "name"), optionalString(o, "uuid"), IdentityMode.fromWire(string(o, "identity_mode")));
    }
    static JsonObject withPlayer(Player p) {
        JsonObject o = new JsonObject(); o.add("player", player(p)); return o;
    }
    static JsonObject envelope(ClientOptions options, String boot, long seq, String kind, String type, String requestId, JsonElement payload) {
        JsonObject o = new JsonObject();
        o.addProperty("protocol_version", Protocol.VERSION);
        o.addProperty("kind", kind); o.addProperty("type", type);
        o.addProperty("message_id", java.util.UUID.randomUUID().toString());
        if (!requestId.isEmpty()) o.addProperty("request_id", requestId);
        o.addProperty("node_id", options.nodeId); o.addProperty("boot_id", boot);
        o.addProperty("sequence", seq); o.addProperty("occurred_at", Instant.now().toString());
        if (payload != null) o.add("payload", payload);
        return o;
    }
}
