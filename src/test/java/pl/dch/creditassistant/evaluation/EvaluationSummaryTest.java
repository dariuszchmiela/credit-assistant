package pl.dch.creditassistant.evaluation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-009 summary without Ollama: a run whose setup failed before any case produced a result must still be sane.
 */
class EvaluationSummaryTest {

    @Test
    void shouldSummarizeAnEmptyRunWithZeroCountsUnknownModelAndTheGivenDuration() {
        String summary = EvaluationSummary.format(List.of(), Duration.ofMillis(1_500));

        assertThat(summary)
                .contains("Passed: 0\n")
                .contains("Failed: 0\n")
                .contains("Errors: 0\n")
                .contains("Model: unknown\n")
                .contains("LLM calls: 0\n")
                .contains("Duration: 1 s\n");
    }
}
