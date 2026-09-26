package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import pl.dch.creditassistant.chat.application.ChatInvocationParameters;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Performs RAG through the delegate and stamps the advisor interaction identifier onto the augmented user message.
 * <p>
 * Why: LangChain4j 1.20 AI Services call the chat model without passing the invocation context to
 * {@code ChatModelListener}s. The augmented user message, however, is the one sent in every model call of the
 * invocation (including the calls after tool executions), and user message attributes are internal metadata
 * that is not sent to the model provider. {@link AiObservabilityChatModelListener} reads the identifier from there.
 */
class InteractionCorrelatingRetrievalAugmentor implements RetrievalAugmentor {

    private static final String INTERACTION_ID_ATTRIBUTE =
            InteractionCorrelatingRetrievalAugmentor.class.getName() + ".interactionId";

    private final RetrievalAugmentor delegate;

    InteractionCorrelatingRetrievalAugmentor(RetrievalAugmentor delegate) {
        this.delegate = delegate;
    }

    @Override
    public AugmentationResult augment(AugmentationRequest request) {
        AugmentationResult result = delegate.augment(request);
        UUID interactionId = interactionId(request.metadata());

        if (interactionId == null || !(result.chatMessage() instanceof UserMessage userMessage)) {
            return result;
        }

        return AugmentationResult.builder()
                .chatMessage(withInteractionId(userMessage, interactionId))
                .contents(result.contents())
                .build();
    }

    static UserMessage withInteractionId(UserMessage userMessage, UUID interactionId) {
        Map<String, Object> attributes = new HashMap<>(userMessage.attributes());
        attributes.put(INTERACTION_ID_ATTRIBUTE, interactionId);

        return userMessage.toBuilder().attributes(attributes).build();
    }

    /**
     * @return the advisor interaction stamped on the user message, or {@code null} when it carries none
     */
    static UUID interactionIdOf(ChatMessage message) {
        if (message instanceof UserMessage userMessage
                && userMessage.attributes().get(INTERACTION_ID_ATTRIBUTE) instanceof UUID interactionId) {
            return interactionId;
        }
        return null;
    }

    private UUID interactionId(Metadata metadata) {
        return metadata == null ? null : ChatInvocationParameters.interactionId(metadata.invocationParameters());
    }
}
