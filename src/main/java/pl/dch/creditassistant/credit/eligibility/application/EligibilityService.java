package pl.dch.creditassistant.credit.eligibility.application;

import org.springframework.stereotype.Service;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityDecision;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityReasonCode;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityRequest;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityResult;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mock eligibility policy (FR-005). Rules are evaluated in a fixed order and the first failed rule decides.
 */
@Service
public class EligibilityService {

    static final BigDecimal MINIMUM_MONTHLY_INCOME = new BigDecimal("3000");
    static final BigDecimal MAXIMUM_OBLIGATIONS_TO_INCOME_RATIO = new BigDecimal("0.50");
    static final BigDecimal MAXIMUM_LOAN_TO_DISPOSABLE_INCOME_MULTIPLIER = new BigDecimal("12");

    private static final int MONEY_SCALE = 2;
    private static final int RATIO_SCALE = 4;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public EligibilityResult checkEligibility(EligibilityRequest request) {
        if (isIncomeBelowMinimum(request)) {
            return incomeTooLow(request);
        }

        if (areObligationsTooHigh(request)) {
            return obligationsTooHigh(request);
        }

        BigDecimal maximumLoanAmount = calculateMaximumLoanAmount(request);

        if (request.requestedLoanAmount().compareTo(maximumLoanAmount) > 0) {
            return loanAmountTooHigh(request, maximumLoanAmount);
        }

        return eligible(request, maximumLoanAmount);
    }

    private boolean isIncomeBelowMinimum(EligibilityRequest request) {
        return request.monthlyIncome().compareTo(MINIMUM_MONTHLY_INCOME) < 0;
    }

    private boolean areObligationsTooHigh(EligibilityRequest request) {
        BigDecimal maximumObligations = request.monthlyIncome().multiply(MAXIMUM_OBLIGATIONS_TO_INCOME_RATIO);

        return request.existingMonthlyObligations().compareTo(maximumObligations) > 0;
    }

    private BigDecimal calculateMaximumLoanAmount(EligibilityRequest request) {
        return request.monthlyDisposableIncome().multiply(MAXIMUM_LOAN_TO_DISPOSABLE_INCOME_MULTIPLIER);
    }

    private EligibilityResult incomeTooLow(EligibilityRequest request) {
        String explanation = "Monthly income of " + formatMoney(request.monthlyIncome())
                + " is below the minimum required monthly income of " + formatMoney(MINIMUM_MONTHLY_INCOME) + ".";

        return notEligible(EligibilityReasonCode.INCOME_TOO_LOW, explanation);
    }

    private EligibilityResult obligationsTooHigh(EligibilityRequest request) {
        String explanation = "Existing monthly obligations of " + formatMoney(request.existingMonthlyObligations())
                + " are " + formatObligationsToIncomeRatio(request)
                + " of monthly income of " + formatMoney(request.monthlyIncome())
                + ", which exceeds the maximum allowed ratio of "
                + formatPercentage(MAXIMUM_OBLIGATIONS_TO_INCOME_RATIO) + ".";

        return notEligible(EligibilityReasonCode.OBLIGATIONS_TOO_HIGH, explanation);
    }

    private EligibilityResult loanAmountTooHigh(EligibilityRequest request, BigDecimal maximumLoanAmount) {
        String explanation = "Requested loan amount of " + formatMoney(request.requestedLoanAmount())
                + " exceeds the maximum allowed loan amount of " + formatMoney(maximumLoanAmount)
                + describeMaximumLoanAmount(request) + ".";

        return notEligible(EligibilityReasonCode.LOAN_AMOUNT_TOO_HIGH, explanation);
    }

    private EligibilityResult eligible(EligibilityRequest request, BigDecimal maximumLoanAmount) {
        String explanation = "Monthly income of " + formatMoney(request.monthlyIncome())
                + " meets the minimum of " + formatMoney(MINIMUM_MONTHLY_INCOME)
                + ", existing monthly obligations are " + formatObligationsToIncomeRatio(request)
                + " of income (maximum " + formatPercentage(MAXIMUM_OBLIGATIONS_TO_INCOME_RATIO) + ")"
                + ", and the requested loan amount of " + formatMoney(request.requestedLoanAmount())
                + " does not exceed the maximum allowed loan amount of " + formatMoney(maximumLoanAmount)
                + describeMaximumLoanAmount(request) + ".";

        return new EligibilityResult(EligibilityDecision.ELIGIBLE, EligibilityReasonCode.ELIGIBLE, explanation);
    }

    private EligibilityResult notEligible(EligibilityReasonCode reasonCode, String explanation) {
        return new EligibilityResult(EligibilityDecision.NOT_ELIGIBLE, reasonCode, explanation);
    }

    private String describeMaximumLoanAmount(EligibilityRequest request) {
        return " (" + MAXIMUM_LOAN_TO_DISPOSABLE_INCOME_MULTIPLIER.toPlainString()
                + " times the monthly disposable income of " + formatMoney(request.monthlyDisposableIncome()) + ")";
    }

    private String formatObligationsToIncomeRatio(EligibilityRequest request) {
        BigDecimal ratio = request.existingMonthlyObligations()
                .divide(request.monthlyIncome(), RATIO_SCALE, RoundingMode.HALF_UP);

        return formatPercentage(ratio);
    }

    private String formatPercentage(BigDecimal ratio) {
        return ratio.multiply(ONE_HUNDRED).setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
