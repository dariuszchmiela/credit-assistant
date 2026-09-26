package pl.dch.creditassistant.privacy.domain;

/**
 * FR-007: supported categories of personally identifiable information.
 */
public enum PiiCategory {
    PESEL,
    CONTRACT_NUMBER;

    /**
     * Deterministic placeholder for the n-th distinct value of this category within one text, e.g. {@code [PESEL_1]}.
     */
    public String placeholder(int sequenceNumber) {
        return "[" + name() + "_" + sequenceNumber + "]";
    }
}
