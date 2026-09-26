package pl.dch.creditassistant.credit.eligibility.domain;

import java.math.BigDecimal;

public record EligibilityRequest(
        BigDecimal monthlyIncome,
        BigDecimal existingMonthlyObligations,
        BigDecimal requestedLoanAmount
) {

    public EligibilityRequest {
        requirePositive(monthlyIncome, "monthlyIncome");
        requireNonNegative(existingMonthlyObligations, "existingMonthlyObligations");
        requirePositive(requestedLoanAmount, "requestedLoanAmount");
    }

    public BigDecimal monthlyDisposableIncome() {
        return monthlyIncome.subtract(existingMonthlyObligations);
    }

    private static void requirePositive(BigDecimal value, String name) {
        requirePresent(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        requirePresent(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void requirePresent(BigDecimal value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
