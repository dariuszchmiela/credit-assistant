package pl.dch.creditassistant.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPEC 79: outcome of one evaluation case, with usage data taken from the persisted observability records.
 *
 * @param inputTokens  {@code null} when at least one model call did not report token usage
 * @param outputTokens {@code null} when at least one model call did not report token usage
 * @param cost         {@code null} when at least one model call has no estimated cost
 */
record EvaluationResult(
        String caseId,
        EvaluationCategory category,
        Status status,
        List<String> failures,
        List<String> modelIdentifiers,
        int llmCalls,
        Long inputTokens,
        Long outputTokens,
        BigDecimal cost,
        long durationMillis
) {

    enum Status {
        PASS,
        FAIL,
        ERROR
    }
}
