package pl.dch.creditassistant.knowledge.application;

import java.util.regex.Pattern;

/**
 * AC-005 / spec §24: normalizes layout only; never rewrites the wording of product rules.
 */
public final class TextNormalizer {

    private static final Pattern WINDOWS_OR_OLD_MAC_LINE_ENDING = Pattern.compile("\r\n?");
    private static final Pattern REPEATED_INLINE_WHITESPACE = Pattern.compile("[ \t]+");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("(?m)[ \t]+$");
    private static final Pattern MORE_THAN_ONE_BLANK_LINE = Pattern.compile("\n{3,}");

    private static final String LINE_FEED = "\n";
    private static final String SINGLE_SPACE = " ";
    private static final String PARAGRAPH_BREAK = "\n\n";

    public String normalize(String text) {
        String normalized = WINDOWS_OR_OLD_MAC_LINE_ENDING.matcher(text).replaceAll(LINE_FEED);
        normalized = REPEATED_INLINE_WHITESPACE.matcher(normalized).replaceAll(SINGLE_SPACE);
        normalized = TRAILING_WHITESPACE.matcher(normalized).replaceAll("");
        normalized = MORE_THAN_ONE_BLANK_LINE.matcher(normalized).replaceAll(PARAGRAPH_BREAK);

        return normalized.strip();
    }
}
