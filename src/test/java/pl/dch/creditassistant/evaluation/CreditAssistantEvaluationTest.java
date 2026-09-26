package pl.dch.creditassistant.evaluation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.dch.creditassistant.TestcontainersConfiguration;
import pl.dch.creditassistant.chat.application.ChatService;
import pl.dch.creditassistant.knowledge.application.KnowledgeRetriever;
import pl.dch.creditassistant.knowledge.domain.KnowledgeChunk;
import pl.dch.creditassistant.privacy.application.PiiMasker;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * FR-009 / SPEC 68-80: runs the evaluation set against the real application with the real configured LLM
 * (Ollama, {@code langchain4j.ollama.chat-model.*}), real RAG over Testcontainers pgvector and real tools.
 * <p>
 * Tagged {@value #TAG}: excluded from the normal build and run only with {@code mvn -Pai-evaluation test}.
 * Tool selection, call counts, correlation and PII are asserted on the persisted observability records;
 * answers are checked with tolerant fact/concept expectations. No LLM is used as a judge.
 */
@Tag(CreditAssistantEvaluationTest.TAG)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CreditAssistantEvaluationTest {

    static final String TAG = "ai-evaluation";

    private static final String MODEL_NAME_PROPERTY = "langchain4j.ollama.chat-model.model-name";
    private static final String BASE_URL_PROPERTY = "langchain4j.ollama.chat-model.base-url";
    private static final Duration AVAILABILITY_TIMEOUT = Duration.ofSeconds(10);
    private static final String SUCCESS = "SUCCESS";
    private static final String REDACTED = "[REDACTED]";

    private static final List<EvaluationResult> RESULTS = new CopyOnWriteArrayList<>();
    private static long suiteStartNanos;

    @Autowired
    private ChatService chatService;

    @Autowired
    private KnowledgeRetriever knowledgeRetriever;

    @Autowired
    private PiiMasker piiMasker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @TestFactory
    Stream<DynamicTest> evaluationSet() throws IOException, InterruptedException {
        requireConfiguredModelIsAvailable();
        suiteStartNanos = System.nanoTime();

        return EvaluationDataset.load().cases().stream()
                .map(evaluationCase -> DynamicTest.dynamicTest(
                        evaluationCase.id() + " " + evaluationCase.category(), () -> evaluate(evaluationCase)));
    }

    @AfterAll
    static void printSummary() {
        System.out.println(EvaluationSummary.format(RESULTS, Duration.ofNanos(System.nanoTime() - suiteStartNanos)));
    }

    private void evaluate(EvaluationCase evaluationCase) {
        deleteObservabilityRecords();
        EvaluationCase.Expectations expectations = evaluationCase.expectations();
        List<String> failures = new ArrayList<>();
        long startNanos = System.nanoTime();
        String answer = null;

        try {
            failures.addAll(retrievalFailures(evaluationCase));
            answer = chatService.chat(evaluationCase.question());
            failures.addAll(AnswerExpectations.violations(answer, expectations));
            failures.addAll(executionFailures(expectations));
            failures.addAll(piiFailures(expectations));
        } catch (RuntimeException exception) {
            record(evaluationCase, EvaluationResult.Status.ERROR, List.of("error: " + exception.getClass().getName()),
                    startNanos);
            throw new AssertionError(diagnostic(evaluationCase, EvaluationResult.Status.ERROR,
                    List.of("error: " + exception.getClass().getName() + ": " + exception.getMessage()), answer));
        }

        EvaluationResult.Status status = failures.isEmpty() ? EvaluationResult.Status.PASS : EvaluationResult.Status.FAIL;
        record(evaluationCase, status, failures, startNanos);
        if (status != EvaluationResult.Status.PASS) {
            throw new AssertionError(diagnostic(evaluationCase, status, failures, answer));
        }
    }

    /**
     * SPEC 74: retrieval quality on its own, through the application API with the same masked query as at runtime.
     */
    private List<String> retrievalFailures(EvaluationCase evaluationCase) {
        List<String> expectedDocuments = evaluationCase.expectations().retrievalDocumentIds();
        if (expectedDocuments.isEmpty()) {
            return List.of();
        }

        String maskedQuestion = piiMasker.mask(evaluationCase.question()).maskedText().text();
        List<String> retrievedDocuments = knowledgeRetriever.retrieve(maskedQuestion).stream()
                .map(KnowledgeChunk::documentId)
                .distinct()
                .toList();

        return expectedDocuments.stream()
                .filter(documentId -> !retrievedDocuments.contains(documentId))
                .map(documentId -> "retrieval did not return document '" + documentId + "'; retrieved " + retrievedDocuments)
                .toList();
    }

    /**
     * SPEC 72 / 75: actual execution evidence from the observability records of the advisor interaction.
     */
    private List<String> executionFailures(EvaluationCase.Expectations expectations) {
        List<String> failures = new ArrayList<>();
        List<Map<String, Object>> advisorInteractions = jdbcTemplate.queryForList("SELECT * FROM advisor_interaction");
        if (advisorInteractions.size() != 1) {
            return List.of("expected 1 advisor interaction, found " + advisorInteractions.size());
        }

        Map<String, Object> advisorInteraction = advisorInteractions.getFirst();
        UUID interactionId = (UUID) advisorInteraction.get("interaction_id");
        if (!SUCCESS.equals(advisorInteraction.get("status"))) {
            failures.add("advisor interaction status is " + advisorInteraction.get("status"));
        }

        List<Map<String, Object>> llmCalls = jdbcTemplate.queryForList("SELECT * FROM ai_interaction");
        if (llmCalls.size() != expectations.expectedLlmCallCount()) {
            failures.add("expected " + expectations.expectedLlmCallCount() + " LLM calls, found " + llmCalls.size());
        }
        if (!llmCalls.stream().allMatch(call -> interactionId.equals(call.get("advisor_interaction_id")))) {
            failures.add("not all LLM calls are correlated with advisor interaction " + interactionId);
        }

        List<Map<String, Object>> toolInvocations = jdbcTemplate.queryForList(
                "SELECT * FROM tool_invocation ORDER BY started_at");
        List<String> executedTools = toolInvocations.stream().map(tool -> (String) tool.get("tool_name")).toList();
        if (!sorted(executedTools).equals(sorted(expectations.expectedTools()))) {
            failures.add("expected executed tools " + expectations.expectedTools() + ", actually executed " + executedTools);
        }
        if (!toolInvocations.stream().allMatch(tool -> interactionId.equals(tool.get("advisor_interaction_id"))
                && SUCCESS.equals(tool.get("status")))) {
            failures.add("tool invocations are not all successful and correlated with " + interactionId);
        }

        return failures;
    }

    /**
     * FR-007 at the persisted boundary: raw values never stored, placeholders present in the masked message.
     */
    private List<String> piiFailures(EvaluationCase.Expectations expectations) {
        if (expectations.rawPiiValues().isEmpty() && expectations.expectedPlaceholders().isEmpty()) {
            return List.of();
        }

        List<String> failures = new ArrayList<>();
        List<String> persistedTexts = new ArrayList<>();
        persistedTexts.addAll(jdbcTemplate.queryForList(
                "SELECT masked_advisor_message FROM advisor_interaction UNION ALL "
                        + "SELECT masked_final_response FROM advisor_interaction", String.class));
        persistedTexts.addAll(jdbcTemplate.queryForList(
                "SELECT masked_user_prompt FROM ai_interaction UNION ALL "
                        + "SELECT masked_model_response FROM ai_interaction", String.class));

        for (int index = 0; index < expectations.rawPiiValues().size(); index++) {
            String rawValue = expectations.rawPiiValues().get(index).toLowerCase(Locale.ROOT);
            boolean leaked = persistedTexts.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(text -> text.toLowerCase(Locale.ROOT).contains(rawValue));
            if (leaked) {
                failures.add("raw PII value #" + (index + 1) + " found in persisted observability content");
            }
        }

        String maskedAdvisorMessage = jdbcTemplate.queryForObject(
                "SELECT masked_advisor_message FROM advisor_interaction", String.class);
        for (String placeholder : expectations.expectedPlaceholders()) {
            if (maskedAdvisorMessage == null || !maskedAdvisorMessage.contains(placeholder)) {
                failures.add("masked advisor message does not contain placeholder " + placeholder);
            }
        }

        return failures;
    }

    private void record(
            EvaluationCase evaluationCase,
            EvaluationResult.Status status,
            List<String> failures,
            long startNanos
    ) {
        List<Map<String, Object>> llmCalls = jdbcTemplate.queryForList(
                "SELECT model_identifier, input_token_count, output_token_count, estimated_cost FROM ai_interaction");

        RESULTS.add(new EvaluationResult(
                evaluationCase.id(),
                evaluationCase.category(),
                status,
                failures,
                llmCalls.stream().map(call -> (String) call.get("model_identifier")).filter(Objects::nonNull).distinct().toList(),
                llmCalls.size(),
                sumOrUnknown(llmCalls, "input_token_count"),
                sumOrUnknown(llmCalls, "output_token_count"),
                costOrUnknown(llmCalls),
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
        ));
    }

    private Long sumOrUnknown(List<Map<String, Object>> llmCalls, String column) {
        if (llmCalls.stream().anyMatch(call -> call.get(column) == null)) {
            return null;
        }
        return llmCalls.stream().mapToLong(call -> ((Number) call.get(column)).longValue()).sum();
    }

    private BigDecimal costOrUnknown(List<Map<String, Object>> llmCalls) {
        if (llmCalls.stream().anyMatch(call -> call.get("estimated_cost") == null)) {
            return null;
        }
        return llmCalls.stream().map(call -> (BigDecimal) call.get("estimated_cost")).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Failure details without synthetic PII: the question is masked and raw values are redacted everywhere.
     */
    private String diagnostic(
            EvaluationCase evaluationCase,
            EvaluationResult.Status status,
            List<String> failures,
            String answer
    ) {
        String interactionId = jdbcTemplate.queryForList("SELECT interaction_id FROM advisor_interaction", UUID.class)
                .stream().map(UUID::toString).findFirst().orElse("none");
        String executedTools = jdbcTemplate.queryForList("SELECT tool_name FROM tool_invocation", String.class).toString();
        String text = """
                %s %s %s
                question:        %s
                failures:        %s
                executed tools:  %s
                interaction id:  %s
                answer:          %s
                """.formatted(evaluationCase.id(), evaluationCase.category(), status,
                evaluationCase.question(), failures, executedTools, interactionId, answer);

        return redact(piiMasker.mask(text).maskedText().text(), evaluationCase.expectations().rawPiiValues());
    }

    private String redact(String text, List<String> rawValues) {
        String redacted = text;
        for (String rawValue : rawValues) {
            redacted = redacted.replaceAll("(?i)" + Pattern.quote(rawValue), REDACTED);
        }
        return redacted;
    }

    private void deleteObservabilityRecords() {
        jdbcTemplate.update("DELETE FROM tool_invocation");
        jdbcTemplate.update("DELETE FROM ai_interaction");
        jdbcTemplate.update("DELETE FROM advisor_interaction");
    }

    private void requireConfiguredModelIsAvailable() throws IOException, InterruptedException {
        String baseUrl = environment.getRequiredProperty(BASE_URL_PROPERTY);
        String modelName = environment.getRequiredProperty(MODEL_NAME_PROPERTY);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                .timeout(AVAILABILITY_TIMEOUT)
                .GET()
                .build();

        try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(AVAILABILITY_TIMEOUT).build()) {
            String models = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            if (!models.contains("\"name\":\"" + modelName + "\"")) {
                throw new IllegalStateException("AI evaluation setup error: model '" + modelName
                        + "' is not available in Ollama at " + baseUrl + " (run: ollama pull " + modelName + ")");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("AI evaluation setup error: Ollama is not reachable at " + baseUrl, exception);
        }
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }
}
