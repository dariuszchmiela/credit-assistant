package pl.dch.creditassistant.chat.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRecorder;
import pl.dch.creditassistant.observability.domain.AdvisorInteraction;
import pl.dch.creditassistant.privacy.application.PiiMasker;
import pl.dch.creditassistant.privacy.domain.PiiMaskingResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * FR-007 AI boundary and owner of the advisor interaction lifecycle (FR-008, SPEC 51-55).
 * <p>
 * The raw advisor message is masked before it reaches the LLM (and RAG). Original values and the interaction
 * identifier travel only in the internal invocation parameters, so every LLM call and tool execution of this
 * request can be correlated with the same advisor interaction.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final PiiMasker piiMasker;
    private final CreditAssistant creditAssistant;
    private final AdvisorInteractionRecorder advisorInteractionRecorder;

    public ChatService(
            PiiMasker piiMasker,
            CreditAssistant creditAssistant,
            AdvisorInteractionRecorder advisorInteractionRecorder
    ) {
        this.piiMasker = piiMasker;
        this.creditAssistant = creditAssistant;
        this.advisorInteractionRecorder = advisorInteractionRecorder;
    }

    public String chat(String rawMessage) {
        UUID interactionId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        log.info("Started advisor interaction {}", interactionId);

        PiiMaskingResult masking = maskOrReject(rawMessage, interactionId, startedAt, startNanos);
        log.info("Advisor interaction {} masked, detected PII categories: {}",
                interactionId, masking.maskedText().detectedCategories());

        AdvisorInteraction started = AdvisorInteraction.started(interactionId, startedAt, masking.maskedText().text());
        advisorInteractionRecorder.record(started);

        try {
            String answer = creditAssistant.chat(
                    masking.maskedText().text(),
                    ChatInvocationParameters.of(interactionId, masking.protectedValues())
            );
            advisorInteractionRecorder.record(started.succeeded(Instant.now(), elapsedMillis(startNanos), mask(answer)));
            log.info("Completed advisor interaction {} status=SUCCESS", interactionId);

            return answer;
        } catch (RuntimeException exception) {
            advisorInteractionRecorder.record(started.failed(Instant.now(), elapsedMillis(startNanos)));
            log.warn("Failed advisor interaction {}: {}", interactionId, exception.getClass().getName());
            throw exception;
        }
    }

    /**
     * Fails closed (SPEC 47): without successful masking the model is not called and nothing of the message is kept.
     */
    private PiiMaskingResult maskOrReject(String rawMessage, UUID interactionId, Instant startedAt, long startNanos) {
        try {
            return piiMasker.mask(rawMessage);
        } catch (RuntimeException exception) {
            advisorInteractionRecorder.record(AdvisorInteraction.rejectedByPrivacy(
                    interactionId, startedAt, Instant.now(), elapsedMillis(startNanos)));
            log.warn("Rejected advisor interaction {}: PII masking failed ({})",
                    interactionId, exception.getClass().getName());
            // The cause is deliberately not attached: its message could contain the raw advisor message.
            throw new IllegalStateException("Advisor message rejected: PII masking failed");
        }
    }

    /**
     * Defensive masking of the final answer before it is persisted; the answer returned to the advisor is unchanged.
     */
    private String mask(String answer) {
        return answer == null ? null : piiMasker.mask(answer).maskedText().text();
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
