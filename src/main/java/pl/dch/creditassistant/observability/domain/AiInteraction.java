package pl.dch.creditassistant.observability.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FR-008, SPEC 10.5 / 53: one physical call of an external chat model (an "LLM call"), part of an
 * {@link AdvisorInteraction} identified by {@code advisorInteractionId}; {@code id} identifies this single call.
 * Contains masked content only. Values the provider did not report are {@code null}, never invented:
 * token counts and cost may be unknown, and failed calls have no response, token counts or cost.
 *
 * @param advisorInteractionId parent advisor interaction; {@code null} for model calls outside an advisor chat
 * @param maskedModelResponse textual model answer; {@code null} for failed calls and tool-call-only responses
 * @param toolNames           names of the tools the model requested in this response (never their arguments)
 * @param errorType           safe error classification of a failed call (exception type, never its message)
 */
public record AiInteraction(
        UUID id,
        UUID advisorInteractionId,
        Instant timestamp,
        String modelIdentifier,
        AiInteractionStatus status,
        String maskedUserPrompt,
        String maskedModelResponse,
        Integer inputTokenCount,
        Integer outputTokenCount,
        BigDecimal estimatedCost,
        long durationMillis,
        List<String> toolNames,
        String errorType
) {

    public AiInteraction {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(timestamp, "timestamp is required");
        Objects.requireNonNull(status, "status is required");
        toolNames = List.copyOf(toolNames);
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }

    public static AiInteraction success(
            UUID advisorInteractionId,
            Instant timestamp,
            String modelIdentifier,
            String maskedUserPrompt,
            String maskedModelResponse,
            Integer inputTokenCount,
            Integer outputTokenCount,
            BigDecimal estimatedCost,
            long durationMillis,
            List<String> toolNames
    ) {
        return new AiInteraction(UUID.randomUUID(), advisorInteractionId, timestamp, modelIdentifier,
                AiInteractionStatus.SUCCESS,
                maskedUserPrompt, maskedModelResponse, inputTokenCount, outputTokenCount, estimatedCost,
                durationMillis, toolNames, null);
    }

    public static AiInteraction failure(
            UUID advisorInteractionId,
            Instant timestamp,
            String modelIdentifier,
            String maskedUserPrompt,
            long durationMillis,
            String errorType
    ) {
        return new AiInteraction(UUID.randomUUID(), advisorInteractionId, timestamp, modelIdentifier,
                AiInteractionStatus.ERROR,
                maskedUserPrompt, null, null, null, null, durationMillis, List.of(), errorType);
    }
}
