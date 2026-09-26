package pl.dch.creditassistant.observability.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * SPEC 54 / 63: one actual execution of an agent tool within an advisor interaction.
 * Unlike the tool names requested in an {@link AiInteraction}, this records that the tool really ran.
 * Tool arguments and results are never recorded.
 */
public record ToolInvocation(
        UUID toolInvocationId,
        UUID advisorInteractionId,
        String toolName,
        Instant startedAt,
        long durationMillis,
        ToolInvocationStatus status
) {

    public ToolInvocation {
        Objects.requireNonNull(toolInvocationId, "toolInvocationId is required");
        Objects.requireNonNull(advisorInteractionId, "advisorInteractionId is required");
        Objects.requireNonNull(toolName, "toolName is required");
        Objects.requireNonNull(startedAt, "startedAt is required");
        Objects.requireNonNull(status, "status is required");
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }
}
