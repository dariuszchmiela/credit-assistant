package pl.dch.creditassistant.privacy.application;

import org.springframework.stereotype.Service;
import pl.dch.creditassistant.privacy.domain.MaskedText;
import pl.dch.creditassistant.privacy.domain.PiiCategory;
import pl.dch.creditassistant.privacy.domain.PiiMaskingResult;
import pl.dch.creditassistant.privacy.domain.ProtectedValues;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FR-007, SPEC 11.4 / 39-41: deterministic masking of supported PII without an LLM.
 * <p>
 * Each distinct value gets a placeholder numbered per category in order of first appearance
 * ({@code [CONTRACT_NUMBER_1]}, {@code [PESEL_1]}, ...); repeated values reuse their placeholder.
 */
@Service
public class PiiMasker {

    /**
     * Contract numbers of the sample bank: {@code CTR-<digits>} (e.g. CTR-1001) and the SPEC 39.2 format
     * {@code CR-<year>-<6 digits>} (e.g. CR-2026-000123); case-insensitive, as a standalone token.
     */
    private static final Pattern CONTRACT_NUMBER = Pattern.compile(
            "\\b(?:CTR-\\d+|CR-\\d{4}-\\d{6})\\b", Pattern.CASE_INSENSITIVE);

    /**
     * PESEL-like value: exactly 11 digits not embedded in a longer digit sequence. The checksum is deliberately
     * not verified, so synthetic or mistyped PESEL numbers are masked too.
     */
    private static final Pattern PESEL = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");


    public PiiMaskingResult mask(String text) {
        Set<PiiCategory> detectedCategories = EnumSet.noneOf(PiiCategory.class);

        CategoryMasking contractNumbers = maskCategory(
                text, PiiCategory.CONTRACT_NUMBER, CONTRACT_NUMBER, PiiMasker::canonicalContractNumber, detectedCategories);
        CategoryMasking pesels = maskCategory(
                contractNumbers.maskedText(), PiiCategory.PESEL, PESEL, UnaryOperator.identity(), detectedCategories);

        // Only contract numbers are needed by internal tools. PESEL masking is not reversible:
        // the PESEL mapping is discarded here and never leaves this method.
        return new PiiMaskingResult(
                new MaskedText(pesels.maskedText(), detectedCategories),
                ProtectedValues.of(Map.of(PiiCategory.CONTRACT_NUMBER, contractNumbers.canonicalValuesByPlaceholder()))
        );
    }

    /**
     * Canonical form used by the credit system: upper-case prefix, e.g. {@code ctr-1001} becomes {@code CTR-1001}.
     */
    private static String canonicalContractNumber(String contractNumber) {
        return contractNumber.toUpperCase(Locale.ROOT);
    }

    /**
     * Replaces every match with the placeholder of its canonical value, so values differing only in form
     * (e.g. letter case) share one placeholder.
     */
    private CategoryMasking maskCategory(
            String text,
            PiiCategory category,
            Pattern pattern,
            UnaryOperator<String> canonicalForm,
            Set<PiiCategory> detectedCategories
    ) {
        Map<String, String> placeholdersByCanonicalValue = new LinkedHashMap<>();
        Matcher matcher = pattern.matcher(text);
        StringBuilder masked = new StringBuilder();

        while (matcher.find()) {
            String canonicalValue = canonicalForm.apply(matcher.group());
            String placeholder = placeholdersByCanonicalValue.computeIfAbsent(
                    canonicalValue, value -> category.placeholder(placeholdersByCanonicalValue.size() + 1));
            matcher.appendReplacement(masked, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(masked);

        if (!placeholdersByCanonicalValue.isEmpty()) {
            detectedCategories.add(category);
        }

        Map<String, String> canonicalValuesByPlaceholder = new LinkedHashMap<>();
        placeholdersByCanonicalValue.forEach((value, placeholder) -> canonicalValuesByPlaceholder.put(placeholder, value));

        return new CategoryMasking(masked.toString(), canonicalValuesByPlaceholder);
    }

    private record CategoryMasking(
            String maskedText,
            Map<String, String> canonicalValuesByPlaceholder
    ) {
    }
}
