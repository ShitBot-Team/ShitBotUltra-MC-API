package shitbotteam.ultra.minecraft.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Capabilities {
    public static final String CHAT_SEND = "chat.send";
    public static final String PLAYER_KICK = "player.kick";
    public static final String COMMAND_EXECUTE = "command.execute";
    public static final String INVENTORY_READ = "inventory.read";
    public static final String TPS_READ = "tps.read";
    public static final String PERMISSION_CHECK = "permission.check";
    public static final String PLACEHOLDER_RESOLVE = "placeholder.resolve";
    public static final Set<String> ALL = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
        CHAT_SEND, PLAYER_KICK, COMMAND_EXECUTE, INVENTORY_READ, TPS_READ, PERMISSION_CHECK, PLACEHOLDER_RESOLVE)));
    private Capabilities() { }
}
