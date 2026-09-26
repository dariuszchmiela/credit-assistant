package pl.dch.creditassistant.observability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * SPEC 52: one advisor request (one {@code POST /api/chat}), the parent of its LLM calls ({@link AiInteraction})
 * and tool executions ({@link ToolInvocation}). Contains masked content only.
 * <p>
 * While the request is running, {@code completedAt}, {@code status} and {@code durationMillis} are {@code null};
 * they are set together when it completes.
 *
 * @param maskedAdvisorMessage {@code null} only when masking itself failed ({@code REJECTED_PRIVACY})
 * @param maskedFinalResponse  {@code null} unless the interaction succeeded
 */
public record AdvisorInteraction(
        UUID interactionId,
        Instant startedAt,
        Instant completedAt,
        String maskedAdvisorMessage,
        String maskedFinalResponse,
        AdvisorInteractionStatus status,
        Long durationMillis
) {

    public AdvisorInteraction {
        Objects.requireNonNull(interactionId, "interactionId is required");
        Objects.requireNonNull(startedAt, "startedAt is required");
        boolean completed = status != null;
        if (completed != (completedAt != null) || completed != (durationMillis != null)) {
            throw new IllegalArgumentException("status, completedAt and durationMillis must be set together");
        }
        if (durationMillis != null && durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }

    public static AdvisorInteraction started(UUID interactionId, Instant startedAt, String maskedAdvisorMessage) {
        return new AdvisorInteraction(interactionId, startedAt, null, maskedAdvisorMessage, null, null, null);
    }

    /**
     * Masking failed before any model call; nothing of the advisor message is kept.
     */
    public static AdvisorInteraction rejectedByPrivacy(
            UUID interactionId,
            Instant startedAt,
            Instant completedAt,
            long durationMillis
    ) {
        return new AdvisorInteraction(interactionId, startedAt, completedAt, null, null,
                AdvisorInteractionStatus.REJECTED_PRIVACY, durationMillis);
    }

    public AdvisorInteraction succeeded(Instant completedAt, long durationMillis, String maskedFinalResponse) {
        return new AdvisorInteraction(interactionId, startedAt, completedAt, maskedAdvisorMessage, maskedFinalResponse,
                AdvisorInteractionStatus.SUCCESS, durationMillis);
    }

    public AdvisorInteraction failed(Instant completedAt, long durationMillis) {
        return new AdvisorInteraction(interactionId, startedAt, completedAt, maskedAdvisorMessage, null,
                AdvisorInteractionStatus.FAILED, durationMillis);
    }
}
