package pl.dch.creditassistant.evaluation;

import java.util.List;

/**
 * One scenario of the evaluation set ({@code evaluation/credit-assistant-evaluation.json}).
 */
record EvaluationCase(
        String id,
        EvaluationCategory category,
        String question,
        Expectations expectations
) {

    /**
     * Only expectations meaningful for the scenario are set; missing lists mean "nothing expected".
     *
     * @param retrievalDocumentIds knowledge documents that retrieval must return for the (masked) question
     * @param expectedTools        tools that must actually execute, exactly (empty = no tool may execute)
     * @param expectedLlmCallCount physical model calls of the advisor interaction
     * @param requiredFacts        exact values the answer must contain (e.g. deterministic tool results)
     * @param requiredConcepts     concepts the answer must express (any alternative pattern matches)
     * @param forbiddenConcepts    concepts the answer must not express (no alternative pattern may match)
     * @param rawPiiValues         synthetic PII of the question that must not appear in persisted observability content
     * @param expectedPlaceholders placeholders the persisted, masked advisor message must contain
     */
    record Expectations(
            List<String> retrievalDocumentIds,
            List<String> expectedTools,
            Integer expectedLlmCallCount,
            List<String> requiredFacts,
            List<Concept> requiredConcepts,
            List<Concept> forbiddenConcepts,
            List<String> rawPiiValues,
            List<String> expectedPlaceholders
    ) {

        Expectations {
            retrievalDocumentIds = orEmpty(retrievalDocumentIds);
            expectedTools = orEmpty(expectedTools);
            requiredFacts = orEmpty(requiredFacts);
            requiredConcepts = orEmpty(requiredConcepts);
            forbiddenConcepts = orEmpty(forbiddenConcepts);
            rawPiiValues = orEmpty(rawPiiValues);
            expectedPlaceholders = orEmpty(expectedPlaceholders);
        }

        private static <T> List<T> orEmpty(List<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    /**
     * @param name  human-readable concept name used in failure diagnostics
     * @param anyOf case-insensitive regular expressions; the concept is present when any of them matches
     */
    record Concept(
            String name,
            List<String> anyOf
    ) {
    }
}
