package pl.dch.creditassistant.evaluation;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Console summary of an evaluation run. Usage values come from the persisted observability records;
 * unknown token counts or costs are shown as unknown, never as zero.
 */
final class EvaluationSummary {

    private static final String UNKNOWN = "unknown";

    private EvaluationSummary() {
    }

    static String format(List<EvaluationResult> results, Duration duration) {
        StringBuilder summary = new StringBuilder("\nAI Evaluation\n=============\n");
        for (EvaluationResult result : results) {
            summary.append("%-9s %-31s %s%n".formatted(result.caseId(), result.category(), result.status()));
        }

        summary.append('\n')
                .append("Passed: ").append(count(results, EvaluationResult.Status.PASS)).append('\n')
                .append("Failed: ").append(count(results, EvaluationResult.Status.FAIL)).append('\n')
                .append("Errors: ").append(count(results, EvaluationResult.Status.ERROR)).append('\n')
                .append("Model: ").append(models(results)).append('\n')
                .append("LLM calls: ").append(results.stream().mapToInt(EvaluationResult::llmCalls).sum()).append('\n')
                .append("Input tokens: ").append(sum(results.stream().map(EvaluationResult::inputTokens).toList())).append('\n')
                .append("Output tokens: ").append(sum(results.stream().map(EvaluationResult::outputTokens).toList())).append('\n')
                .append("Estimated cost: ").append(cost(results)).append('\n')
                .append("Duration: ").append(duration.toSeconds()).append(" s\n");

        return summary.toString();
    }

    private static long count(List<EvaluationResult> results, EvaluationResult.Status status) {
        return results.stream().filter(result -> result.status() == status).count();
    }

    private static String models(List<EvaluationResult> results) {
        List<String> models = results.stream()
                .flatMap(result -> result.modelIdentifiers().stream())
                .distinct()
                .toList();

        return models.isEmpty() ? UNKNOWN : String.join(", ", models);
    }

    private static String sum(Collection<Long> values) {
        return values.stream().anyMatch(Objects::isNull)
                ? UNKNOWN
                : String.valueOf(values.stream().mapToLong(Long::longValue).sum());
    }

    private static String cost(List<EvaluationResult> results) {
        return results.stream().anyMatch(result -> result.cost() == null)
                ? UNKNOWN
                : results.stream().map(EvaluationResult::cost).reduce(BigDecimal.ZERO, BigDecimal::add).toPlainString();
    }
}
