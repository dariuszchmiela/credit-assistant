package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pl.dch.creditassistant.observability.application.AiCostCalculator;
import pl.dch.creditassistant.observability.application.AiInteractionRecorder;
import pl.dch.creditassistant.observability.application.AiInteractionRepository;
import pl.dch.creditassistant.observability.domain.AiInteraction;
import pl.dch.creditassistant.observability.domain.AiInteractionStatus;
import pl.dch.creditassistant.privacy.application.PiiMasker;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * FR-008: mapping of one chat model call to one AI interaction, using real LangChain4j listener contexts,
 * the real masker and cost calculator, and an in-memory repository. No LLM, no database.
 */
@ExtendWith(OutputCaptureExtension.class)
class AiObservabilityChatModelListenerTest {

    private static final String RAW_PESEL = "44051401458";
    private static final String RAW_CONTRACT_NUMBER = "CTR-1001";
    private static final String MASKED_PROMPT = "Status of [CONTRACT_NUMBER_1]?";
    private static final String REQUEST_MODEL = "configured-model";
    private static final String REPORTED_MODEL = "reported-model:latest";

    private final List<AiInteraction> savedInteractions = new ArrayList<>();
    private final AiInteractionRepository inMemoryRepository = savedInteractions::add;
    private final AiCostCalculator costCalculator = new AiCostCalculator(new BigDecimal("2"), new BigDecimal("10"));
    private final AiObservabilityChatModelListener listener = new AiObservabilityChatModelListener(
            new AiInteractionRecorder(inMemoryRepository, costCalculator), new PiiMasker());

    @Test
    void shouldRecordOneSuccessfulInteractionWithMetadataUsageAndCost() {
        Instant before = Instant.now();

        callModel(request(MASKED_PROMPT), response(AiMessage.from("Contract [CONTRACT_NUMBER_1] is ACTIVE."),
                REPORTED_MODEL, new TokenUsage(1_000, 200)));

        assertThat(savedInteractions).singleElement().satisfies(interaction -> {
            assertThat(interaction.status()).isEqualTo(AiInteractionStatus.SUCCESS);
            assertThat(interaction.timestamp()).isBetween(before, Instant.now());
            assertThat(interaction.modelIdentifier()).isEqualTo(REPORTED_MODEL);
            assertThat(interaction.maskedUserPrompt()).isEqualTo(MASKED_PROMPT);
            assertThat(interaction.maskedModelResponse()).isEqualTo("Contract [CONTRACT_NUMBER_1] is ACTIVE.");
            assertThat(interaction.inputTokenCount()).isEqualTo(1_000);
            assertThat(interaction.outputTokenCount()).isEqualTo(200);
            assertThat(interaction.estimatedCost()).isEqualByComparingTo("0.004");
            assertThat(interaction.durationMillis()).isNotNegative();
            assertThat(interaction.toolNames()).isEmpty();
            assertThat(interaction.errorType()).isNull();
        });
    }

    @Test
    void shouldLinkCallToAdvisorInteractionStampedOnTheUserMessage() {
        UUID interactionId = UUID.randomUUID();
        ChatRequest correlatedRequest = ChatRequest.builder()
                .messages(SystemMessage.from("system instructions"),
                        InteractionCorrelatingRetrievalAugmentor.withInteractionId(
                                UserMessage.from(MASKED_PROMPT), interactionId))
                .modelName(REQUEST_MODEL)
                .build();

        callModel(correlatedRequest, response(AiMessage.from("answer"), REPORTED_MODEL, new TokenUsage(1, 1)));
        callModel(request(MASKED_PROMPT), response(AiMessage.from("answer"), REPORTED_MODEL, new TokenUsage(1, 1)));

        assertThat(savedInteractions).extracting(AiInteraction::advisorInteractionId)
                .as("correlated call, then a call without advisor interaction")
                .containsExactly(interactionId, null);
        assertThat(savedInteractions.getFirst().id()).isNotEqualTo(interactionId);
        assertThat(savedInteractions.getFirst().maskedUserPrompt()).isEqualTo(MASKED_PROMPT);
    }

    @Test
    void shouldRecordRequestedToolNamesWithoutArgumentsOrText() {
        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1")
                .name("getContractStatus")
                .arguments("{\"contractReference\":\"[CONTRACT_NUMBER_1]\"}")
                .build());

        callModel(request(MASKED_PROMPT), response(toolCall, REPORTED_MODEL, new TokenUsage(900, 20)));

        assertThat(savedInteractions).singleElement().satisfies(interaction -> {
            assertThat(interaction.toolNames()).containsExactly("getContractStatus");
            assertThat(interaction.maskedModelResponse()).isNull();
            assertThat(interaction.toString()).doesNotContain("contractReference");
        });
    }

    @Test
    void shouldMaskPiiThatAppearsInModelResponseOrPromptBeforeRecording() {
        callModel(request("Regression leaked " + RAW_CONTRACT_NUMBER + " and " + RAW_PESEL),
                response(AiMessage.from("The contract " + RAW_CONTRACT_NUMBER + " belongs to PESEL " + RAW_PESEL),
                        REPORTED_MODEL, new TokenUsage(10, 10)));

        assertThat(savedInteractions).singleElement().satisfies(interaction -> {
            assertThat(interaction.maskedUserPrompt()).isEqualTo("Regression leaked [CONTRACT_NUMBER_1] and [PESEL_1]");
            assertThat(interaction.maskedModelResponse())
                    .isEqualTo("The contract [CONTRACT_NUMBER_1] belongs to PESEL [PESEL_1]");
            assertThat(interaction.toString()).doesNotContain(RAW_CONTRACT_NUMBER).doesNotContain(RAW_PESEL);
        });
    }

    @Test
    void shouldRecordLastUserMessageOnlyAndFallBackToRequestModel() {
        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from("system instructions"),
                        UserMessage.from("previous question"),
                        AiMessage.from("previous answer"),
                        UserMessage.from(MASKED_PROMPT))
                .modelName(REQUEST_MODEL)
                .build();

        callModel(request, ChatResponse.builder().aiMessage(AiMessage.from("answer")).build());

        assertThat(savedInteractions).singleElement().satisfies(interaction -> {
            assertThat(interaction.maskedUserPrompt()).isEqualTo(MASKED_PROMPT);
            assertThat(interaction.modelIdentifier()).isEqualTo(REQUEST_MODEL);
        });
    }

    @Test
    void shouldLeaveTokenCountsAndCostUnknownWhenUsageIsNotReported() {
        callModel(request(MASKED_PROMPT), response(AiMessage.from("answer"), REPORTED_MODEL, null));

        assertThat(savedInteractions).singleElement().satisfies(interaction -> {
            assertThat(interaction.status()).isEqualTo(AiInteractionStatus.SUCCESS);
            assertThat(interaction.inputTokenCount()).isNull();
            assertThat(interaction.outputTokenCount()).isNull();
            assertThat(interaction.estimatedCost()).isNull();
        });
    }

    @Test
    void shouldNotPropagateOrLeakRepositoryFailure(CapturedOutput output) {
        AiInteractionRepository failingRepository = interaction -> {
            throw new IllegalStateException("insert failed for value " + RAW_PESEL);
        };
        AiObservabilityChatModelListener listenerWithFailingRepository = new AiObservabilityChatModelListener(
                new AiInteractionRecorder(failingRepository, costCalculator), new PiiMasker());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatRequest request = request(MASKED_PROMPT);

        listenerWithFailingRepository.onRequest(new ChatModelRequestContext(request, ModelProvider.OLLAMA, attributes));

        assertThatCode(() -> listenerWithFailingRepository.onResponse(new ChatModelResponseContext(
                response(AiMessage.from("answer"), REPORTED_MODEL, new TokenUsage(1, 1)),
                request, ModelProvider.OLLAMA, attributes)))
                .doesNotThrowAnyException();
        assertThat(output)
                .contains("Failed to persist AI interaction")
                .contains(IllegalStateException.class.getName())
                .doesNotContain(RAW_PESEL)
                .doesNotContain(MASKED_PROMPT);
    }

    @Test
    void shouldRecordSafeFailureWithoutResponseUsageCostOrErrorMessage(CapturedOutput output) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatRequest request = request("Status of " + RAW_CONTRACT_NUMBER + "?");
        RuntimeException providerError = new RuntimeException("provider rejected " + RAW_CONTRACT_NUMBER + " " + RAW_PESEL);

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OLLAMA, attributes));
        listener.onError(new ChatModelErrorContext(providerError, request, ModelProvider.OLLAMA, attributes));

        assertThat(savedInteractions).singleElement().satisfies(interaction -> {
            assertThat(interaction.status()).isEqualTo(AiInteractionStatus.ERROR);
            assertThat(interaction.errorType()).isEqualTo(RuntimeException.class.getName());
            assertThat(interaction.modelIdentifier()).isEqualTo(REQUEST_MODEL);
            assertThat(interaction.maskedUserPrompt()).isEqualTo(MASKED_PROMPT);
            assertThat(interaction.maskedModelResponse()).isNull();
            assertThat(interaction.inputTokenCount()).isNull();
            assertThat(interaction.outputTokenCount()).isNull();
            assertThat(interaction.estimatedCost()).isNull();
            assertThat(interaction.durationMillis()).isNotNegative();
            assertThat(interaction.toString()).doesNotContain("provider rejected").doesNotContain(RAW_PESEL);
        });
        assertThat(output).doesNotContain(RAW_PESEL).doesNotContain(RAW_CONTRACT_NUMBER);
    }

    private void callModel(ChatRequest request, ChatResponse response) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OLLAMA, attributes));
        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OLLAMA, attributes));
    }

    private static ChatRequest request(String userText) {
        return ChatRequest.builder()
                .messages(SystemMessage.from("system instructions"), UserMessage.from(userText))
                .modelName(REQUEST_MODEL)
                .build();
    }

    private static ChatResponse response(AiMessage aiMessage, String modelName, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(ChatResponseMetadata.builder().modelName(modelName).tokenUsage(tokenUsage).build())
                .build();
    }
}
