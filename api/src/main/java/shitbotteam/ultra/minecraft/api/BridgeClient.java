package shitbotteam.ultra.minecraft.api;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * One client per game process/node. Methods never block waiting for network I/O.
 * Completion callbacks are not game-thread callbacks. Never join/get a future on a game thread.
 */
public interface BridgeClient extends AutoCloseable {
    /** Single-use managed lifecycle. Future completes at first READY; retries continue until close. */
    CompletableFuture<ConnectionStatus> start();
    ConnectionStatus status();
    Subscription onStatus(Consumer<ConnectionStatus> listener);
    CompletableFuture<LoginDecision> authorize(Player player, Duration timeout);
    CompletableFuture<BindingStatus> binding(String playerName, Duration timeout);
    /** Generic Core request. Current Core only supports login.authorize and binding.get. */
    CompletableFuture<JsonData> request(String type, JsonData payload, Duration timeout);
    CompletableFuture<EventReceipt> chat(ChatMessage message);
    CompletableFuture<EventReceipt> joined(Player player);
    CompletableFuture<EventReceipt> left(Player player);
    CompletableFuture<EventReceipt> players(Collection<Player> players);
    CompletableFuture<EventReceipt> inventory(InventorySnapshot snapshot);
    CompletableFuture<EventReceipt> serverReady();
    /** Nonblocking, idempotent cancellation. A new instance is required to restart. */
    @Override void close();
    CompletableFuture<Void> whenClosed();
}
