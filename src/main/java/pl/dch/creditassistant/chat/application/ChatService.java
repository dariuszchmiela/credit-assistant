package pl.dch.creditassistant.chat.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.dch.creditassistant.privacy.application.PiiMasker;
import pl.dch.creditassistant.privacy.domain.PiiMaskingResult;

/**
 * FR-007 AI boundary: the raw advisor message is masked before it reaches the LLM (and RAG).
 * Original values travel only in the internal invocation parameters, for deterministic tools.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final PiiMasker piiMasker;
    private final CreditAssistant creditAssistant;

    public ChatService(PiiMasker piiMasker, CreditAssistant creditAssistant) {
        this.piiMasker = piiMasker;
        this.creditAssistant = creditAssistant;
    }

    public String chat(String rawMessage) {
        PiiMaskingResult masking = piiMasker.mask(rawMessage);
        log.info("Chat request masked, detected PII categories: {}", masking.maskedText().detectedCategories());

        return creditAssistant.chat(
                masking.maskedText().text(),
                ChatInvocationParameters.withProtectedValues(masking.protectedValues())
        );
    }
}
