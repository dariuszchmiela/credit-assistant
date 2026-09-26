package pl.dch.creditassistant.privacy.domain;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Internal mapping from placeholders back to canonical original values, for deterministic services inside
 * the trusted application boundary (SPEC 42). It holds only values that internal tools must resolve;
 * currently contract numbers. PESEL masking is not reversible, so PESEL values are never stored here.
 * Never sent to an LLM, logged or persisted: {@link #toString()} deliberately reveals no values.
 */
public final class ProtectedValues {

    private static final ProtectedValues NONE = new ProtectedValues(new EnumMap<>(PiiCategory.class));

    private final Map<PiiCategory, Map<String, String>> originalsByCategory;

    private ProtectedValues(Map<PiiCategory, Map<String, String>> originalsByCategory) {
        this.originalsByCategory = originalsByCategory;
    }

    public static ProtectedValues none() {
        return NONE;
    }

    public static ProtectedValues of(Map<PiiCategory, Map<String, String>> originalsByPlaceholder) {
        Map<PiiCategory, Map<String, String>> copy = new EnumMap<>(PiiCategory.class);
        originalsByPlaceholder.forEach((category, originals) -> copy.put(category, Map.copyOf(originals)));

        return new ProtectedValues(copy);
    }

    /**
     * Resolves a placeholder of the given category; placeholders of other categories never resolve.
     */
    public Optional<String> originalOf(PiiCategory category, String placeholder) {
        return Optional.ofNullable(originalsByCategory.getOrDefault(category, Map.of()).get(placeholder));
    }

    @Override
    public String toString() {
        Map<PiiCategory, Integer> counts = new HashMap<>();
        originalsByCategory.forEach((category, originals) -> counts.put(category, originals.size()));

        return "ProtectedValues" + counts;
    }
}
