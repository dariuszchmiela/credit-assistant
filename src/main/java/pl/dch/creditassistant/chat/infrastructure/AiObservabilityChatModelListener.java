package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.observability.application.AiInteractionRecorder;
import pl.dch.creditassistant.privacy.application.PiiMasker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FR-008 / AC-003: observes every physical chat model call (one record per call, so a tool round trip yields two).
 * Attached automatically by the LangChain4j Spring Boot starter to the chat model.
 * <p>
 * The recorded prompt is the last user message actually sent to the model (masked by {@code ChatService},
 * possibly augmented by RAG). Prompt and response are masked again before recording as defence in depth;
 * tool arguments, invocation parameters and error messages are never recorded.
 * <p>
 * Each call is linked to its advisor interaction through the identifier that
 * {@link InteractionCorrelatingRetrievalAugmentor} stamps on the user message.
 */
@Component
public class AiObservabilityChatModelListener implements ChatModelListener {

    private static final String START_TIMESTAMP = AiObservabilityChatModelListener.class.getName() + ".startTimestamp";
    private static final String START_NANOS = AiObservabilityChatModelListener.class.getName() + ".startNanos";
    private static final String TEXT_SEPARATOR = "\n";

    private final AiInteractionRecorder recorder;
    private final PiiMasker piiMasker;

    public AiObservabilityChatModelListener(AiInteractionRecorder recorder, PiiMasker piiMasker) {
        this.recorder = recorder;
        this.piiMasker = piiMasker;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(START_TIMESTAMP, Instant.now());
        requestContext.attributes().put(START_NANOS, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        ChatResponse response = responseContext.chatResponse();
        ChatRequest request = responseContext.chatRequest();
        AiMessage aiMessage = response.aiMessage();
        TokenUsage tokenUsage = response.metadata() == null ? null : response.metadata().tokenUsage();

        recorder.recordSuccess(
                advisorInteractionId(request),
                startTimestamp(responseContext.attributes()),
                modelIdentifier(response, request),
                maskedUserPrompt(request),
                mask(aiMessage.text()),
                tokenUsage == null ? null : tokenUsage.inputTokenCount(),
                tokenUsage == null ? null : tokenUsage.outputTokenCount(),
                elapsedMillis(responseContext.attributes()),
                requestedToolNames(aiMessage)
        );
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        recorder.recordFailure(
                advisorInteractionId(errorContext.chatRequest()),
                startTimestamp(errorContext.attributes()),
                errorContext.chatRequest().modelName(),
                maskedUserPrompt(errorContext.chatRequest()),
                elapsedMillis(errorContext.attributes()),
                errorContext.error().getClass().getName()
        );
    }

    private String modelIdentifier(ChatResponse response, ChatRequest request) {
        String reportedModel = response.metadata() == null ? null : response.metadata().modelName();

        return reportedModel != null ? reportedModel : request.modelName();
    }

    private UUID advisorInteractionId(ChatRequest request) {
        return lastUserMessage(request)
                .map(InteractionCorrelatingRetrievalAugmentor::interactionIdOf)
                .orElse(null);
    }

    private String maskedUserPrompt(ChatRequest request) {
        return lastUserMessage(request)
                .map(userMessage -> mask(text(userMessage)))
                .orElse(null);
    }

    private Optional<UserMessage> lastUserMessage(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index) instanceof UserMessage userMessage) {
                return Optional.of(userMessage);
            }
        }
        return Optional.empty();
    }

    private String text(UserMessage userMessage) {
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining(TEXT_SEPARATOR));
    }

    private List<String> requestedToolNames(AiMessage aiMessage) {
        if (!aiMessage.hasToolExecutionRequests()) {
            return List.of();
        }

        return aiMessage.toolExecutionRequests().stream()
                .map(ToolExecutionRequest::name)
                .toList();
    }

    private String mask(String text) {
        return text == null || text.isBlank() ? null : piiMasker.mask(text).maskedText().text();
    }

    private Instant startTimestamp(Map<Object, Object> attributes) {
        Object startTimestamp = attributes.get(START_TIMESTAMP);

        return startTimestamp instanceof Instant instant ? instant : Instant.now();
    }

    private long elapsedMillis(Map<Object, Object> attributes) {
        Object startNanos = attributes.get(START_NANOS);
        if (!(startNanos instanceof Long start)) {
            return 0;
        }

        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }
}
