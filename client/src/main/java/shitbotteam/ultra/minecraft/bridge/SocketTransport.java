package shitbotteam.ultra.minecraft.bridge;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.*;
import okio.ByteString;
import shitbotteam.ultra.minecraft.api.Protocol;

/** One owned HTTP client, no redirects/auth logging/custom trust bypass. */
final class SocketTransport implements AutoCloseable {
    interface Listener {
        void opened(WebSocket socket);
        void text(String message);
        void lost(String code, boolean permanent);
    }
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .minWebSocketMessageToCompress(Long.MAX_VALUE).build();
    private volatile WebSocket active;

    WebSocket connect(ClientOptions options, Listener listener) {
        String value = options.token.get();
        if (value == null) throw new IllegalArgumentException("Node credential unavailable");
        value = value.trim();
        if (value.length() < 32 || value.length() > 4096) throw new IllegalArgumentException("Node credential has invalid length");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x21 || c > 0x7e) throw new IllegalArgumentException("Node credential must be printable ASCII without whitespace");
        }
        Request request = new Request.Builder().url(options.endpoint.toString())
                .header("Authorization", "Bearer " + value).header("X-SBU-Node-ID", options.nodeId).build();
        AtomicBoolean terminal = new AtomicBoolean();
        WebSocket socket = http.newWebSocket(request, new WebSocketListener() {
            private void lost(WebSocket ws, String code, boolean permanent) {
                ws.cancel();
                if (terminal.compareAndSet(false, true)) listener.lost(code, permanent);
            }
            @Override public void onOpen(WebSocket ws, Response response) {
                if (response.header("Sec-WebSocket-Extensions") != null) { lost(ws, "PROTOCOL_MISMATCH", true); return; }
                if (terminal.get()) { ws.cancel(); return; }
                listener.opened(ws);
            }
            @Override public void onMessage(WebSocket ws, String text) {
                if (text.length() > Protocol.MAX_MESSAGE_BYTES || text.getBytes(StandardCharsets.UTF_8).length > Protocol.MAX_MESSAGE_BYTES) {
                    lost(ws, "MESSAGE_TOO_LARGE", true); return;
                }
                if (!terminal.get()) listener.text(text);
            }
            @Override public void onMessage(WebSocket ws, ByteString bytes) { lost(ws, "PROTOCOL_MISMATCH", true); }
            @Override public void onClosing(WebSocket ws, int code, String reason) { lost(ws, "NODE_OFFLINE", code == 1008); }
            @Override public void onClosed(WebSocket ws, int code, String reason) { lost(ws, "NODE_OFFLINE", code == 1008); }
            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                int status = response == null ? 0 : response.code();
                if (response != null) response.close();
                boolean permanent = status == 400 || status == 401 || status == 403 || status == 404 || (status >= 300 && status < 400);
                lost(ws, permanent ? "AUTH_OR_ENDPOINT_REJECTED" : "NODE_OFFLINE", permanent);
            }
        });
        active = socket;
        return socket;
    }

    @Override public void close() {
        WebSocket socket = active;
        if (socket != null) socket.cancel();
        http.dispatcher().cancelAll();
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdown();
    }
}
