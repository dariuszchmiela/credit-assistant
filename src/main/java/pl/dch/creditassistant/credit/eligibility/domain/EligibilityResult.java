package pl.dch.creditassistant.credit.eligibility.domain;

public record EligibilityResult(
        EligibilityDecision decision,
        EligibilityReasonCode reasonCode,
        String explanation
) {
}
