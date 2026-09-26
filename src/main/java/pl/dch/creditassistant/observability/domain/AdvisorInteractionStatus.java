package pl.dch.creditassistant.observability.domain;

/**
 * SPEC 52: final status of an advisor interaction. An interaction that has not completed yet has no status.
 */
public enum AdvisorInteractionStatus {
    SUCCESS,
    FAILED,
    REJECTED_PRIVACY
}
