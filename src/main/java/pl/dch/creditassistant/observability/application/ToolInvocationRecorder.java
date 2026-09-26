package pl.dch.creditassistant.observability.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.dch.creditassistant.observability.domain.ToolInvocation;
import pl.dch.creditassistant.observability.domain.ToolInvocationStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * SPEC 54 / 63: times the actual execution of an agent tool and records it as a {@link ToolInvocation}.
 * The tool's result or exception is passed through unchanged; recording problems never affect the tool.
 */
public class ToolInvocationRecorder {

    private static final Logger log = LoggerFactory.getLogger(ToolInvocationRecorder.class);

    private final ToolInvocationRepository repository;

    public ToolInvocationRecorder(ToolInvocationRepository repository) {
        this.repository = repository;
    }

    /**
     * Executes the tool and records its duration and outcome.
     *
     * @param advisorInteractionId interaction the tool runs in; when missing, the tool runs without being recorded
     */
    public <T> T execute(UUID advisorInteractionId, String toolName, Supplier<T> toolExecution) {
        if (advisorInteractionId == null) {
            log.warn("Tool {} executed without advisor interaction context; invocation not recorded", toolName);
            return toolExecution.get();
        }

        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        try {
            T result = toolExecution.get();
            save(advisorInteractionId, toolName, startedAt, startNanos, ToolInvocationStatus.SUCCESS);
            return result;
        } catch (RuntimeException exception) {
            save(advisorInteractionId, toolName, startedAt, startNanos, ToolInvocationStatus.ERROR);
            throw exception;
        }
    }

    private void save(
            UUID advisorInteractionId,
            String toolName,
            Instant startedAt,
            long startNanos,
            ToolInvocationStatus status
    ) {
        long durationMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        ToolInvocation invocation = new ToolInvocation(
                UUID.randomUUID(), advisorInteractionId, toolName, startedAt, durationMillis, status);
        try {
            repository.save(invocation);
        } catch (RuntimeException exception) {
            // The exception message may contain SQL parameter values, so only its type is logged.
            log.warn("Failed to persist tool invocation {} of advisor interaction {}: {}",
                    invocation.toolInvocationId(), advisorInteractionId, exception.getClass().getName());
        }
    }
}
