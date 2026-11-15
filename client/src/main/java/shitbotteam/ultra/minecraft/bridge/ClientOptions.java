package shitbotteam.ultra.minecraft.bridge;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import shitbotteam.ultra.minecraft.api.*;

/** Immutable connection metadata. Secrets are evaluated for every connection attempt. */
public final class ClientOptions {
    final URI endpoint;
    final String nodeId, networkId, serverId, adapterVersion, platform, minecraftVersion;
    final IdentityMode identityMode;
    final Supplier<String> token;
    final Map<String, ActionHandler> actions;
    final SnapshotProvider snapshots;

    private ClientOptions(Builder b) {
        endpoint = b.endpoint;
        nodeId = b.nodeId;
        networkId = b.networkId;
        serverId = b.serverId;
        identityMode = b.identityMode;
        adapterVersion = b.adapterVersion;
        platform = b.platform;
        minecraftVersion = b.minecraftVersion;
        token = Objects.requireNonNull(b.token, "token supplier");
        snapshots = b.snapshots;
        actions = Collections.unmodifiableMap(new LinkedHashMap<String, ActionHandler>(b.actions));
    }

    public static Builder builder(URI endpoint, String nodeId, String networkId, String serverId, IdentityMode mode) {
        return new Builder(endpoint, nodeId, networkId, serverId, mode);
    }

    public static final class Builder {
        private final URI endpoint;
        private final String nodeId, networkId, serverId;
        private final IdentityMode identityMode;
        private String adapterVersion = "0.1.0", platform = "custom", minecraftVersion = "unknown";
        private Supplier<String> token;
        private SnapshotProvider snapshots;
        private final Map<String, ActionHandler> actions = new LinkedHashMap<String, ActionHandler>();

        private Builder(URI endpoint, String nodeId, String networkId, String serverId, IdentityMode mode) {
            this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
            String scheme = endpoint.getScheme();
            String host = endpoint.getHost();
            if (!("ws".equals(scheme) || "wss".equals(scheme)) || host == null || host.isEmpty()
                    || !"/bridge/v1".equals(endpoint.getRawPath()) || endpoint.getRawUserInfo() != null
                    || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                    || endpoint.getPort() == 0 || endpoint.getPort() > 65535) {
                throw new IllegalArgumentException("Expected ws(s)://host[:port]/bridge/v1 without credentials, query or fragment");
            }
            String literal = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length()-1) : host;
            boolean loopback = literal.equals("localhost") || literal.equals("::1") || literal.equals("0:0:0:0:0:0:0:1") || literal.matches("127(?:\\.[0-9]{1,3}){3}");
            if ("ws".equals(scheme) && !loopback) throw new IllegalArgumentException("Remote Core connections require wss");
            this.nodeId = Protocol.localId(nodeId, "node ID");
            this.networkId = Protocol.localId(networkId, "network ID");
            this.serverId = Protocol.localId(serverId, "server ID");
            this.identityMode = Objects.requireNonNull(mode, "identity mode");
        }

        /** Supplier must be fast and must not log the secret. Use a cached value for file-backed tokens. */
        public Builder token(Supplier<String> supplier) { token = Objects.requireNonNull(supplier); return this; }
        public Builder metadata(String adapterVersion, String platform, String minecraftVersion) {
            this.adapterVersion = Protocol.id(adapterVersion, "adapter version");
            this.platform = Protocol.id(platform, "platform");
            this.minecraftVersion = Protocol.id(minecraftVersion, "Minecraft version");
            return this;
        }
        public Builder snapshots(SnapshotProvider provider) { snapshots = Objects.requireNonNull(provider); return this; }
        /** Registered actions are the exact advertised capabilities; recreate the client to change them. */
        public Builder action(String capability, ActionHandler handler) {
            if (!Capabilities.ALL.contains(capability)) throw new IllegalArgumentException("Unsupported game capability");
            if (actions.containsKey(capability)) throw new IllegalArgumentException("Duplicate action handler");
            actions.put(capability, Objects.requireNonNull(handler));
            return this;
        }
        public ClientOptions build() { return new ClientOptions(this); }
    }
}
