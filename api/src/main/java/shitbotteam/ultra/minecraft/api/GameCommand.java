package shitbotteam.ultra.minecraft.api;

/** Game console command. Never pass to an operating-system shell. */
public final class GameCommand {
    private final String command;
    private final String actorId;
    public GameCommand(String command, String actorId) {
        Protocol.text(command, "game command", 2048);
        if (command.trim().isEmpty() || command.indexOf('\r') >= 0 || command.indexOf('\n') >= 0 || command.indexOf(0) >= 0) throw new IllegalArgumentException("Invalid game command");
        Protocol.id(actorId, "actor ID");
        this.command = java.util.Objects.requireNonNull(command, "command");
        this.actorId = java.util.Objects.requireNonNull(actorId, "actorId");
    }
    public String command() { return command; }
    public String actorId() { return actorId; }
}
