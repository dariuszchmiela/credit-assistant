package pl.dch.creditassistant.chat.application;

import dev.langchain4j.invocation.InvocationParameters;
import pl.dch.creditassistant.privacy.domain.ProtectedValues;

import java.util.Map;
import java.util.UUID;

/**
 * Keys and accessors of the internal, LLM-invisible {@link InvocationParameters} of a chat invocation:
 * the {@link ProtectedValues} for placeholder resolution and the advisor interaction identifier for observability.
 */
public final class ChatInvocationParameters {

    private static final String PROTECTED_VALUES = "protectedValues";
    private static final String INTERACTION_ID = "interactionId";

    private ChatInvocationParameters() {
    }

    static InvocationParameters of(UUID interactionId, ProtectedValues protectedValues) {
        return InvocationParameters.from(Map.of(
                INTERACTION_ID, interactionId,
                PROTECTED_VALUES, protectedValues
        ));
    }

    /**
     * @return the protected values of the current invocation, or none when the invocation carries no PII context
     */
    public static ProtectedValues protectedValues(InvocationParameters invocationParameters) {
        if (invocationParameters == null) {
            return ProtectedValues.none();
        }

        return invocationParameters.getOrDefault(PROTECTED_VALUES, ProtectedValues.none());
    }

    /**
     * @return the advisor interaction of the current invocation, or {@code null} when the invocation has none
     */
    public static UUID interactionId(InvocationParameters invocationParameters) {
        if (invocationParameters == null) {
            return null;
        }

        return invocationParameters.get(INTERACTION_ID);
    }
}
