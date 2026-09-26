package pl.dch.creditassistant.chat.application;

import dev.langchain4j.invocation.InvocationParameters;
import pl.dch.creditassistant.privacy.domain.ProtectedValues;

/**
 * Keys and accessors of the internal, LLM-invisible {@link InvocationParameters} of a chat invocation.
 */
public final class ChatInvocationParameters {

    private static final String PROTECTED_VALUES = "protectedValues";

    private ChatInvocationParameters() {
    }

    static InvocationParameters withProtectedValues(ProtectedValues protectedValues) {
        return InvocationParameters.from(PROTECTED_VALUES, protectedValues);
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
}
