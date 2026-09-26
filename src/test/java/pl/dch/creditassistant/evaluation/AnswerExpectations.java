package pl.dch.creditassistant.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SPEC 73 / 76: tolerant checks of a natural-language answer. Wording may differ; facts and concepts may not.
 * Only presentation is normalized (markdown emphasis, thousands separators), never the values themselves.
 */
final class AnswerExpectations {

    /**
     * Asterisks and backticks only: underscores are part of values such as {@code NOT_ELIGIBLE} or reason codes.
     */
    private static final Pattern MARKDOWN_EMPHASIS = Pattern.compile("[*`]");
    private static final Pattern THOUSANDS_SEPARATOR = Pattern.compile("(?<=\\d)[,\\u00A0\\u202F](?=\\d{3}(?!\\d))");

    private AnswerExpectations() {
    }

    static List<String> violations(String answer, EvaluationCase.Expectations expectations) {
        String normalizedAnswer = normalize(answer);
        String lowerCaseAnswer = normalizedAnswer.toLowerCase(Locale.ROOT);
        List<String> violations = new ArrayList<>();

        for (String fact : expectations.requiredFacts()) {
            if (!lowerCaseAnswer.contains(fact.toLowerCase(Locale.ROOT))) {
                violations.add("answer does not contain required fact '" + fact + "'");
            }
        }
        for (EvaluationCase.Concept concept : expectations.requiredConcepts()) {
            if (!expresses(normalizedAnswer, concept)) {
                violations.add("answer does not express required concept '" + concept.name() + "'");
            }
        }
        for (EvaluationCase.Concept concept : expectations.forbiddenConcepts()) {
            if (expresses(normalizedAnswer, concept)) {
                violations.add("answer expresses forbidden concept '" + concept.name() + "'");
            }
        }

        return violations;
    }

    static String normalize(String answer) {
        String withoutEmphasis = MARKDOWN_EMPHASIS.matcher(answer).replaceAll("");
        return THOUSANDS_SEPARATOR.matcher(withoutEmphasis).replaceAll("");
    }

    static Pattern compile(String conceptPattern) {
        return Pattern.compile(conceptPattern, Pattern.CASE_INSENSITIVE);
    }

    private static boolean expresses(String answer, EvaluationCase.Concept concept) {
        return concept.anyOf().stream().anyMatch(pattern -> compile(pattern).matcher(answer).find());
    }
}
