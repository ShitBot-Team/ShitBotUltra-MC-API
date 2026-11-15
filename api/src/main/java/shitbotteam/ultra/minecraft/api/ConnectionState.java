package shitbotteam.ultra.minecraft.api;

public enum ConnectionState {
    NEW, CONNECTING, HANDSHAKING, SYNCHRONIZING, READY, BACKOFF, FAILED, CLOSED
}
