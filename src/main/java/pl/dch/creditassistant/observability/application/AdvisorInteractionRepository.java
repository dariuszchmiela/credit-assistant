package pl.dch.creditassistant.observability.application;

import pl.dch.creditassistant.observability.domain.AdvisorInteraction;

/**
 * Persistence port for advisor interactions. {@link #save} inserts a new interaction or replaces the state of
 * an existing one with the same identifier (a started interaction is saved again when it completes).
 */
public interface AdvisorInteractionRepository {

    void save(AdvisorInteraction interaction);
}
