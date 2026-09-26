package pl.dch.creditassistant.privacy.domain;

import java.util.Set;

/**
 * SPEC 11.4: text that is safe to send to an external LLM, log or persist. Contains no original PII values.
 *
 * @param text               text with supported PII replaced by placeholders
 * @param detectedCategories categories of PII found in the original text
 */
public record MaskedText(
        String text,
        Set<PiiCategory> detectedCategories
) {

    public MaskedText {
        detectedCategories = Set.copyOf(detectedCategories);
    }
}
