package pl.dch.creditassistant.privacy.domain;

/**
 * Result of masking: the shareable {@link MaskedText} and the internal-only {@link ProtectedValues}.
 * Both components have value-free string representations.
 */
public record PiiMaskingResult(
        MaskedText maskedText,
        ProtectedValues protectedValues
) {
}
