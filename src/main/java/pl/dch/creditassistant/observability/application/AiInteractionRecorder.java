package pl.dch.creditassistant.observability.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.dch.creditassistant.observability.domain.AiInteraction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FR-008: records one AI interaction per physical chat model call. Callers pass masked content only.
 * Recording never fails the observed call: persistence errors are logged without content (SPEC 61).
 */
public class AiInteractionRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiInteractionRecorder.class);

    private final AiInteractionRepository repository;
    private final AiCostCalculator costCalculator;

    public AiInteractionRecorder(AiInteractionRepository repository, AiCostCalculator costCalculator) {
        this.repository = repository;
        this.costCalculator = costCalculator;
    }

    public void recordSuccess(
            UUID advisorInteractionId,
            Instant timestamp,
            String modelIdentifier,
            String maskedUserPrompt,
            String maskedModelResponse,
            Integer inputTokenCount,
            Integer outputTokenCount,
            long durationMillis,
            List<String> toolNames
    ) {
        save(AiInteraction.success(
                advisorInteractionId,
                timestamp,
                modelIdentifier,
                maskedUserPrompt,
                maskedModelResponse,
                inputTokenCount,
                outputTokenCount,
                costCalculator.estimate(inputTokenCount, outputTokenCount).orElse(null),
                durationMillis,
                toolNames
        ));
    }

    public void recordFailure(
            UUID advisorInteractionId,
            Instant timestamp,
            String modelIdentifier,
            String maskedUserPrompt,
            long durationMillis,
            String errorType
    ) {
        save(AiInteraction.failure(
                advisorInteractionId, timestamp, modelIdentifier, maskedUserPrompt, durationMillis, errorType));
    }

    private void save(AiInteraction interaction) {
        try {
            repository.save(interaction);
        } catch (RuntimeException exception) {
            // The exception message may contain SQL parameter values, so only its type is logged.
            log.warn("Failed to persist AI interaction {} of advisor interaction {}: {}",
                    interaction.id(), interaction.advisorInteractionId(), exception.getClass().getName());
        }
    }
}
