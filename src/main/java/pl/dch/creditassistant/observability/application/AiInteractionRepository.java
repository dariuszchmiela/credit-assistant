package pl.dch.creditassistant.observability.application;

import pl.dch.creditassistant.observability.domain.AiInteraction;

/**
 * SPEC 11.6: persistence port for AI interactions. Only masked content is ever passed in.
 */
public interface AiInteractionRepository {

    void save(AiInteraction interaction);
}
