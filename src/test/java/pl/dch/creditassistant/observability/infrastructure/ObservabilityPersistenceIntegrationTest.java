package pl.dch.creditassistant.observability.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.dch.creditassistant.TestcontainersConfiguration;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRepository;
import pl.dch.creditassistant.observability.application.AiInteractionRepository;
import pl.dch.creditassistant.observability.application.ToolInvocationRepository;
import pl.dch.creditassistant.observability.domain.AdvisorInteraction;
import pl.dch.creditassistant.observability.domain.AiInteraction;
import pl.dch.creditassistant.observability.domain.ToolInvocation;
import pl.dch.creditassistant.observability.domain.ToolInvocationStatus;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-008, FR-011, SPEC 51-55 / 60: persistence of advisor interactions, LLM calls and tool invocations against
 * real PostgreSQL (Testcontainers) with the Flyway schema, including their relationships.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ObservabilityPersistenceIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-26T10:15:30.123456Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-26T10:15:32.654321Z");

    @Autowired
    private AdvisorInteractionRepository advisorInteractionRepository;

    @Autowired
    private AiInteractionRepository aiInteractionRepository;

    @Autowired
    private ToolInvocationRepository toolInvocationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM tool_invocation");
        jdbcTemplate.update("DELETE FROM ai_interaction");
        jdbcTemplate.update("DELETE FROM advisor_interaction");
    }

    @Test
    void shouldCreateSchemaThroughFlywayMigration() {
        Integer appliedVersion1 = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class);
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_name IN "
                        + "('advisor_interaction', 'ai_interaction', 'tool_invocation')", String.class);

        assertThat(appliedVersion1).isEqualTo(1);
        assertThat(tables).containsExactlyInAnyOrder("advisor_interaction", "ai_interaction", "tool_invocation");
    }

    @Test
    void shouldInsertStartedAdvisorInteractionAndCompleteItLater() {
        AdvisorInteraction started = AdvisorInteraction.started(UUID.randomUUID(), STARTED_AT, "Status of [CONTRACT_NUMBER_1]?");

        advisorInteractionRepository.save(started);
        Map<String, Object> running = advisorRow(started.interactionId());
        advisorInteractionRepository.save(started.succeeded(COMPLETED_AT, 2_531, "Contract [CONTRACT_NUMBER_1] is ACTIVE."));
        Map<String, Object> completed = advisorRow(started.interactionId());

        assertThat(running)
                .containsEntry("status", null)
                .containsEntry("completed_at", null)
                .containsEntry("duration_millis", null)
                .containsEntry("masked_advisor_message", "Status of [CONTRACT_NUMBER_1]?");
        assertThat(((Timestamp) completed.get("started_at")).toInstant()).isEqualTo(STARTED_AT);
        assertThat(((Timestamp) completed.get("completed_at")).toInstant()).isEqualTo(COMPLETED_AT);
        assertThat(completed)
                .containsEntry("status", "SUCCESS")
                .containsEntry("duration_millis", 2_531L)
                .containsEntry("masked_advisor_message", "Status of [CONTRACT_NUMBER_1]?")
                .containsEntry("masked_final_response", "Contract [CONTRACT_NUMBER_1] is ACTIVE.");
    }

    @Test
    void shouldPersistRejectedAdvisorInteractionWithoutMessage() {
        AdvisorInteraction rejected = AdvisorInteraction.rejectedByPrivacy(UUID.randomUUID(), STARTED_AT, COMPLETED_AT, 3);

        advisorInteractionRepository.save(rejected);

        assertThat(advisorRow(rejected.interactionId()))
                .containsEntry("status", "REJECTED_PRIVACY")
                .containsEntry("masked_advisor_message", null)
                .containsEntry("masked_final_response", null)
                .containsEntry("duration_millis", 3L);
    }

    @Test
    void shouldPersistAllFieldsOfSuccessfulLlmCallLinkedToItsAdvisorInteraction() throws SQLException {
        UUID interactionId = startedAdvisorInteraction();
        AiInteraction llmCall = AiInteraction.success(
                interactionId,
                STARTED_AT,
                "qwen-test:1b",
                "Status of [CONTRACT_NUMBER_1] for [PESEL_1]?",
                "Contract [CONTRACT_NUMBER_1] is ACTIVE.",
                1_234,
                56,
                new BigDecimal("0.0123456789"),
                1_500,
                List.of("getContractStatus", "calculateInstallment")
        );

        aiInteractionRepository.save(llmCall);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM ai_interaction WHERE id = ?", llmCall.id());
        assertThat(((Timestamp) row.get("interaction_timestamp")).toInstant()).isEqualTo(STARTED_AT);
        assertThat(row)
                .containsEntry("advisor_interaction_id", interactionId)
                .containsEntry("model_identifier", "qwen-test:1b")
                .containsEntry("status", "SUCCESS")
                .containsEntry("error_type", null)
                .containsEntry("masked_user_prompt", "Status of [CONTRACT_NUMBER_1] for [PESEL_1]?")
                .containsEntry("masked_model_response", "Contract [CONTRACT_NUMBER_1] is ACTIVE.")
                .containsEntry("input_token_count", 1_234)
                .containsEntry("output_token_count", 56)
                .containsEntry("duration_millis", 1_500L);
        assertThat((BigDecimal) row.get("estimated_cost")).isEqualTo(new BigDecimal("0.0123456789"));
        assertThat(textArray(row.get("tool_names"))).containsExactly("getContractStatus", "calculateInstallment");
    }

    @Test
    void shouldPersistFailedLlmCallWithUnknownValuesAsNull() throws SQLException {
        AiInteraction failure = AiInteraction.failure(
                null, STARTED_AT, null, "Status of [CONTRACT_NUMBER_1]?", 42, "java.net.http.HttpTimeoutException");

        aiInteractionRepository.save(failure);

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM ai_interaction WHERE id = ?", failure.id());
        assertThat(row)
                .containsEntry("advisor_interaction_id", null)
                .containsEntry("status", "ERROR")
                .containsEntry("error_type", "java.net.http.HttpTimeoutException")
                .containsEntry("model_identifier", null)
                .containsEntry("masked_model_response", null)
                .containsEntry("input_token_count", null)
                .containsEntry("output_token_count", null)
                .containsEntry("estimated_cost", null)
                .containsEntry("duration_millis", 42L);
        assertThat(textArray(row.get("tool_names"))).isEmpty();
    }

    @Test
    void shouldPersistToolInvocation() {
        UUID interactionId = startedAdvisorInteraction();
        ToolInvocation invocation = new ToolInvocation(
                UUID.randomUUID(), interactionId, "getContractStatus", STARTED_AT, 7, ToolInvocationStatus.ERROR);

        toolInvocationRepository.save(invocation);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM tool_invocation WHERE tool_invocation_id = ?", invocation.toolInvocationId());
        assertThat(((Timestamp) row.get("started_at")).toInstant()).isEqualTo(STARTED_AT);
        assertThat(row)
                .containsEntry("advisor_interaction_id", interactionId)
                .containsEntry("tool_name", "getContractStatus")
                .containsEntry("duration_millis", 7L)
                .containsEntry("status", "ERROR");
    }

    @Test
    void shouldRelateOneAdvisorInteractionToManyLlmCallsAndToolInvocations() {
        UUID interactionId = startedAdvisorInteraction();
        UUID otherInteractionId = startedAdvisorInteraction();
        aiInteractionRepository.save(llmCall(interactionId, List.of("getContractStatus")));
        aiInteractionRepository.save(llmCall(interactionId, List.of()));
        aiInteractionRepository.save(llmCall(otherInteractionId, List.of()));
        toolInvocationRepository.save(new ToolInvocation(
                UUID.randomUUID(), interactionId, "getContractStatus", STARTED_AT, 4, ToolInvocationStatus.SUCCESS));

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT (SELECT count(*) FROM ai_interaction WHERE advisor_interaction_id = a.interaction_id) AS llm_calls,
                       (SELECT sum(input_token_count + output_token_count) FROM ai_interaction
                         WHERE advisor_interaction_id = a.interaction_id) AS total_tokens,
                       (SELECT string_agg(tool_name, ',') FROM tool_invocation
                         WHERE advisor_interaction_id = a.interaction_id) AS executed_tools
                FROM advisor_interaction a WHERE a.interaction_id = ?
                """, interactionId);

        assertThat(summary)
                .containsEntry("llm_calls", 2L)
                .containsEntry("total_tokens", 220L)
                .containsEntry("executed_tools", "getContractStatus");
    }

    @Test
    void shouldEnforceForeignKeyToAdvisorInteraction() {
        ToolInvocation orphan = new ToolInvocation(
                UUID.randomUUID(), UUID.randomUUID(), "getContractStatus", STARTED_AT, 1, ToolInvocationStatus.SUCCESS);

        assertThatThrownBy(() -> toolInvocationRepository.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID startedAdvisorInteraction() {
        AdvisorInteraction started = AdvisorInteraction.started(UUID.randomUUID(), STARTED_AT, "masked message");
        advisorInteractionRepository.save(started);
        return started.interactionId();
    }

    private AiInteraction llmCall(UUID interactionId, List<String> requestedTools) {
        return AiInteraction.success(interactionId, STARTED_AT, "model", "prompt", "answer", 100, 10,
                BigDecimal.ZERO, 5, requestedTools);
    }

    private Map<String, Object> advisorRow(UUID interactionId) {
        return jdbcTemplate.queryForMap("SELECT * FROM advisor_interaction WHERE interaction_id = ?", interactionId);
    }

    private List<String> textArray(Object sqlArray) throws SQLException {
        return List.of((String[]) ((Array) sqlArray).getArray());
    }
}
