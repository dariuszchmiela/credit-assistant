package pl.dch.creditassistant.evaluation;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * FR-009: the fixed evaluation set, stored as readable JSON in {@code src/test/resources/evaluation}.
 */
record EvaluationDataset(
        String description,
        List<EvaluationCase> cases
) {

    static final String LOCATION = "/evaluation/credit-assistant-evaluation.json";

    static EvaluationDataset load() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        try (InputStream input = EvaluationDataset.class.getResourceAsStream(LOCATION)) {
            if (input == null) {
                throw new IllegalStateException("Evaluation dataset not found: " + LOCATION);
            }
            return jsonMapper.readValue(input, EvaluationDataset.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read evaluation dataset " + LOCATION, exception);
        }
    }
}
