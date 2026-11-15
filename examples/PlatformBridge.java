package shitbotteam.ultra.minecraft.example;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import shitbotteam.ultra.minecraft.api.*;
import shitbotteam.ultra.minecraft.bridge.*;

/**
 * Platform-neutral ownership example, not a Bukkit/Velocity plugin.
 * Not compiled or executed as part of this delivery.
 */
public final class PlatformBridge implements AutoCloseable {
    public interface Platform {
        /**
         * Schedule on the SAME ordered publisher used by joined/left below.
         * Read a consistent player set and call sink.submit directly in that task.
         * Folia integration supplies its own authoritative aggregation.
         */
        void captureSnapshot(SnapshotSink sink);
        /**
         * Schedule on the required game/entity/region thread, check cancellation
         * immediately before the effect, and return a stage without blocking.
         * Use Payloads for standard payloads and results.
         */
        CompletionStage<JsonData> dispatch(ActionRequest request);
        /** Only redactable status codes, never credentials or login codes. */
        void diagnostic(String state, String code);
    }

    private final BridgeClient client;
    private final Subscription statuses;

    public PlatformBridge(URI endpoint, String node, String network, String server,
            IdentityMode mode, Supplier<String> cachedToken, String platformName,
            String minecraftVersion, Set<String> supportedCapabilities, Platform platform) {
        ClientOptions.Builder builder = ClientOptions.builder(endpoint, node, network, server, mode)
                .token(cachedToken).metadata("0.1.0", platformName, minecraftVersion)
                .snapshots(platform::captureSnapshot);
        for (String capability : supportedCapabilities) builder.action(capability, platform::dispatch);
        client = new ManagedBridgeClient(builder.build());
        statuses = client.onStatus(s -> platform.diagnostic(s.state().name(), s.errorCode()));
    }
    public CompletableFuture<ConnectionStatus> start() { return client.start(); }
    /** Publish this single service via the platform's services API for other plugins. */
    public BridgeClient service() { return client; }
    /** Invoke after the platform is actually ready, not merely after opening a socket. */
    public CompletableFuture<EventReceipt> serverReady() { return client.serverReady(); }
    /**
     * The caller must suspend/complete login asynchronously. On failure, apply
     * its explicit offline policy; never turn a transport failure into allow.
     */
    public CompletableFuture<LoginDecision> authorize(Player player) { return client.authorize(player, Duration.ofSeconds(3)); }
    /** These publications and snapshot submission share the platform's ordered publisher. */
    public CompletableFuture<EventReceipt> joined(Player player) { return client.joined(player); }
    public CompletableFuture<EventReceipt> left(Player player) { return client.left(player); }
    public CompletableFuture<EventReceipt> chat(Player player, String text, String originId) {
        return client.chat(new ChatMessage(player, text, originId));
    }
    public CompletableFuture<EventReceipt> inventory(InventorySnapshot snapshot) { return client.inventory(snapshot); }
    @Override public void close() { statuses.close(); client.close(); }
    public CompletableFuture<Void> whenClosed() { return client.whenClosed(); }
}
