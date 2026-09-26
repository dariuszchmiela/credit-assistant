package pl.dch.creditassistant.chat.application;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.dch.creditassistant.TestcontainersConfiguration;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-008 + FR-007: one observability row per physical chat model call, through the real AI service, RAG,
 * tool execution, listener and PostgreSQL. The external LLM is a scripted {@link ChatModel} that reports model
 * name and token usage and notifies the application's {@link ChatModelListener}s like a real provider model.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=dev.langchain4j.ollama.spring.OllamaAutoConfiguration",
        "observability.cost.input-per-million-tokens=2",
        "observability.cost.output-per-million-tokens=10"
})
@Import({TestcontainersConfiguration.class, ChatObservabilityIntegrationTest.ScriptedChatModelConfiguration.class})
class ChatObservabilityIntegrationTest {

    private static final String RAW_CONTRACT_NUMBER = "ctr-1001";
    private static final String RAW_PESEL = "44051401458";
    private static final String MODEL_NAME = "scripted-model:1";

    @Autowired
    private ChatService chatService;

    @Autowired
    private ScriptedChatModel chatModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.update("DELETE FROM tool_invocation");
        jdbcTemplate.update("DELETE FROM ai_interaction");
        jdbcTemplate.update("DELETE FROM advisor_interaction");
        chatModel.failing = false;
    }

    @Test
    void shouldCorrelateAdvisorInteractionLlmCallsAndToolInvocationOfOneRequest() {
        chatService.chat("Can the customer with PESEL " + RAW_PESEL + " repay contract " + RAW_CONTRACT_NUMBER + " early?");

        Map<String, Object> advisorInteraction = jdbcTemplate.queryForMap("SELECT * FROM advisor_interaction");
        UUID interactionId = (UUID) advisorInteraction.get("interaction_id");
        List<Map<String, Object>> llmCalls = jdbcTemplate.queryForList(
                "SELECT * FROM ai_interaction ORDER BY interaction_timestamp, input_token_count");
        Map<String, Object> toolInvocation = jdbcTemplate.queryForMap("SELECT * FROM tool_invocation");

        assertThat(advisorInteraction)
                .containsEntry("status", "SUCCESS")
                .containsEntry("masked_final_response", ScriptedChatModel.ANSWER);
        assertThat((String) advisorInteraction.get("masked_advisor_message")).contains("[CONTRACT_NUMBER_1]", "[PESEL_1]");
        assertThat((Long) advisorInteraction.get("duration_millis")).isNotNegative();

        assertThat(llmCalls).hasSize(2)
                .allSatisfy(call -> assertThat(call).containsEntry("advisor_interaction_id", interactionId));
        assertThat(llmCalls.get(0).get("id")).isNotEqualTo(llmCalls.get(1).get("id"));

        assertThat(toolInvocation)
                .as("actually executed tool, distinct from the tool requested by LLM call #1")
                .containsEntry("advisor_interaction_id", interactionId)
                .containsEntry("tool_name", "getContractStatus")
                .containsEntry("status", "SUCCESS");
        assertThat((Long) toolInvocation.get("duration_millis")).isNotNegative();

        assertThat(List.of(advisorInteraction, toolInvocation).toString() + llmCalls)
                .doesNotContainIgnoringCase(RAW_CONTRACT_NUMBER)
                .doesNotContain(RAW_PESEL);
    }

    @Test
    void shouldUseADifferentInteractionIdForTheNextAdvisorRequest() {
        chatService.chat("Status of " + RAW_CONTRACT_NUMBER + "?");
        chatService.chat("Status of " + RAW_CONTRACT_NUMBER + "?");

        Integer advisorInteractions = jdbcTemplate.queryForObject("SELECT count(*) FROM advisor_interaction", Integer.class);
        List<Integer> llmCallsPerInteraction = jdbcTemplate.queryForList(
                "SELECT count(*)::int FROM ai_interaction GROUP BY advisor_interaction_id", Integer.class);
        List<Integer> toolsPerInteraction = jdbcTemplate.queryForList(
                "SELECT count(*)::int FROM tool_invocation GROUP BY advisor_interaction_id", Integer.class);

        assertThat(advisorInteractions).isEqualTo(2);
        assertThat(llmCallsPerInteraction).containsExactly(2, 2);
        assertThat(toolsPerInteraction).containsExactly(1, 1);
    }

    @Test
    void shouldPersistOneMaskedRecordPerLlmCallOfAToolRoundTrip() throws SQLException {
        chatService.chat("Can the customer with PESEL " + RAW_PESEL + " repay contract " + RAW_CONTRACT_NUMBER + " early?");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_interaction ORDER BY interaction_timestamp, input_token_count");
        assertThat(rows).as("LLM call #1 (tool request) and LLM call #2 (answer)").hasSize(2);

        Map<String, Object> toolCall = rows.get(0);
        Map<String, Object> answer = rows.get(1);

        assertThat(textArray(toolCall.get("tool_names"))).containsExactly("getContractStatus");
        assertThat(textArray(answer.get("tool_names"))).isEmpty();
        assertThat(toolCall.get("masked_model_response")).isNull();
        assertThat(answer.get("masked_model_response")).isEqualTo(ScriptedChatModel.ANSWER);

        assertThat(toolCall).containsEntry("input_token_count", 1_000).containsEntry("output_token_count", 20);
        assertThat(answer).containsEntry("input_token_count", 1_200).containsEntry("output_token_count", 50);
        assertThat((BigDecimal) toolCall.get("estimated_cost")).isEqualByComparingTo("0.0022");
        assertThat((BigDecimal) answer.get("estimated_cost")).isEqualByComparingTo("0.0029");

        assertThat(rows).allSatisfy(row -> {
            assertThat(row).containsEntry("status", "SUCCESS").containsEntry("model_identifier", MODEL_NAME);
            assertThat((Long) row.get("duration_millis")).isNotNegative();
            assertThat((String) row.get("masked_user_prompt"))
                    .contains("[CONTRACT_NUMBER_1]", "[PESEL_1]")
                    .doesNotContainIgnoringCase(RAW_CONTRACT_NUMBER)
                    .doesNotContain(RAW_PESEL);
            assertThat(row.toString()).doesNotContainIgnoringCase(RAW_CONTRACT_NUMBER).doesNotContain(RAW_PESEL);
        });
    }

    @Test
    void shouldPersistSafeErrorRecordWhenTheModelFails() {
        chatModel.failing = true;

        assertThatThrownBy(() -> chatService.chat("Status of " + RAW_CONTRACT_NUMBER + " for PESEL " + RAW_PESEL + "?"))
                .isInstanceOf(RuntimeException.class);

        Map<String, Object> advisorInteraction = jdbcTemplate.queryForMap("SELECT * FROM advisor_interaction");
        assertThat(advisorInteraction)
                .containsEntry("status", "FAILED")
                .containsEntry("masked_final_response", null);
        assertThat((Long) advisorInteraction.get("duration_millis")).isNotNegative();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tool_invocation", Integer.class)).isZero();

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM ai_interaction");
        assertThat(row)
                .containsEntry("advisor_interaction_id", advisorInteraction.get("interaction_id"))
                .containsEntry("status", "ERROR")
                .containsEntry("error_type", IllegalStateException.class.getName())
                .containsEntry("masked_model_response", null)
                .containsEntry("input_token_count", null)
                .containsEntry("output_token_count", null)
                .containsEntry("estimated_cost", null);
        assertThat((Long) row.get("duration_millis")).isNotNegative();
        assertThat((String) row.get("masked_user_prompt")).contains("[CONTRACT_NUMBER_1]", "[PESEL_1]");
        assertThat(row.toString() + advisorInteraction)
                .doesNotContain(ScriptedChatModel.PROVIDER_ERROR_MESSAGE)
                .doesNotContainIgnoringCase(RAW_CONTRACT_NUMBER)
                .doesNotContain(RAW_PESEL);
    }

    private List<String> textArray(Object sqlArray) throws SQLException {
        return List.of((String[]) ((Array) sqlArray).getArray());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedChatModelConfiguration {

        @Bean
        ScriptedChatModel scriptedChatModel(List<ChatModelListener> listeners) {
            return new ScriptedChatModel(listeners);
        }
    }

    /**
     * First call requests {@code getContractStatus}, second call answers; can be switched to fail like a provider.
     * {@link ChatModel#chat(ChatRequest)} notifies {@link #listeners()} around {@link #doChat(ChatRequest)}.
     */
    static class ScriptedChatModel implements ChatModel {

        static final String ANSWER = "Contract [CONTRACT_NUMBER_1] is ACTIVE.";
        static final String PROVIDER_ERROR_MESSAGE = "provider failure echoing request data";

        private final List<ChatModelListener> listeners;
        private volatile boolean failing;

        ScriptedChatModel(List<ChatModelListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return listeners;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            if (failing) {
                throw new IllegalStateException(PROVIDER_ERROR_MESSAGE + " " + RAW_CONTRACT_NUMBER + " " + RAW_PESEL);
            }

            boolean afterToolExecution = chatRequest.messages().getLast() instanceof ToolExecutionResultMessage;

            return afterToolExecution
                    ? response(AiMessage.from(ANSWER), new TokenUsage(1_200, 50))
                    : response(AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-1")
                            .name("getContractStatus")
                            .arguments("{\"contractReference\":\"[CONTRACT_NUMBER_1]\"}")
                            .build()), new TokenUsage(1_000, 20));
        }

        private static ChatResponse response(AiMessage aiMessage, TokenUsage tokenUsage) {
            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(ChatResponseMetadata.builder().modelName(MODEL_NAME).tokenUsage(tokenUsage).build())
                    .build();
        }
    }
}
