package shitbotteam.ultra.minecraft.api;

import java.util.concurrent.CompletionStage;

/** Dispatch onto the correct game/region thread and return immediately. */
@FunctionalInterface
public interface ActionHandler {
    CompletionStage<JsonData> handle(ActionRequest request);
}
