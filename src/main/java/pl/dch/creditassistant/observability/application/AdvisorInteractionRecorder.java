package pl.dch.creditassistant.observability.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.dch.creditassistant.observability.domain.AdvisorInteraction;

/**
 * Records the lifecycle of advisor interactions. Never fails the advisor request: persistence errors are logged
 * with the interaction identifier and exception type only (SPEC 61).
 */
public class AdvisorInteractionRecorder {

    private static final Logger log = LoggerFactory.getLogger(AdvisorInteractionRecorder.class);

    private final AdvisorInteractionRepository repository;

    public AdvisorInteractionRecorder(AdvisorInteractionRepository repository) {
        this.repository = repository;
    }

    public void record(AdvisorInteraction interaction) {
        try {
            repository.save(interaction);
        } catch (RuntimeException exception) {
            // The exception message may contain SQL parameter values, so only its type is logged.
            log.warn("Failed to persist advisor interaction {}: {}",
                    interaction.interactionId(), exception.getClass().getName());
        }
    }
}
