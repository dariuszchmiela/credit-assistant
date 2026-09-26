package pl.dch.creditassistant.credit.eligibility.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityDecision;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityReasonCode;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityRequest;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-005, NFR-005: eligibility policy executed without Spring or an LLM.
 */
class EligibilityServiceTest {

    private final EligibilityService eligibilityService = new EligibilityService();

    @Test
    void shouldBeEligibleWhenAllRulesPass() {
        EligibilityResult result = check("8000", "2000", "40000");

        assertThat(result.decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo(EligibilityReasonCode.ELIGIBLE);
        assertThat(result.explanation())
                .contains("40000.00")
                .contains("72000.00")
                .contains("6000.00");
    }

    @Test
    void shouldRejectWhenIncomeIsBelowMinimum() {
        EligibilityResult result = check("2999.99", "0", "1000");

        assertNotEligible(result, EligibilityReasonCode.INCOME_TOO_LOW);
        assertThat(result.explanation())
                .contains("2999.99")
                .contains("3000.00");
    }

    @Test
    void shouldAcceptIncomeExactlyAtMinimum() {
        EligibilityResult result = check("3000", "0", "1000");

        assertThat(result.decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
    }

    @Test
    void shouldRejectWhenObligationsExceedHalfOfIncome() {
        EligibilityResult result = check("8000", "5000", "20000");

        assertNotEligible(result, EligibilityReasonCode.OBLIGATIONS_TOO_HIGH);
        assertThat(result.explanation())
                .contains("62.50%")
                .contains("50.00%");
    }

    @Test
    void shouldAcceptObligationsExactlyAtHalfOfIncome() {
        EligibilityResult result = check("8000", "4000", "1000");

        assertThat(result.decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
    }

    @Test
    void shouldRejectObligationsJustAboveHalfOfIncome() {
        EligibilityResult result = check("8000", "4000.01", "1000");

        assertNotEligible(result, EligibilityReasonCode.OBLIGATIONS_TOO_HIGH);
    }

    @Test
    void shouldRejectWhenRequestedLoanExceedsTwelveTimesDisposableIncome() {
        EligibilityResult result = check("8000", "2000", "72000.01");

        assertNotEligible(result, EligibilityReasonCode.LOAN_AMOUNT_TOO_HIGH);
        assertThat(result.explanation())
                .contains("72000.01")
                .contains("72000.00");
    }

    @Test
    void shouldAcceptRequestedLoanExactlyAtTwelveTimesDisposableIncome() {
        EligibilityResult result = check("8000", "2000", "72000");

        assertThat(result.decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo(EligibilityReasonCode.ELIGIBLE);
    }

    @Test
    void shouldReportIncomeRuleFirstWhenSeveralRulesFail() {
        EligibilityResult result = check("2000", "1500", "100000");

        assertNotEligible(result, EligibilityReasonCode.INCOME_TOO_LOW);
    }

    @Test
    void shouldReportObligationsRuleBeforeLoanAmountRule() {
        EligibilityResult result = check("8000", "5000", "1000000");

        assertNotEligible(result, EligibilityReasonCode.OBLIGATIONS_TOO_HIGH);
    }

    @ParameterizedTest(name = "income={0}, obligations={1}, loan={2}")
    @CsvSource({
            "0, 0, 1000",
            "-1, 0, 1000",
            "8000, -0.01, 1000",
            "8000, 0, 0",
            "8000, 0, -1",
            ", 0, 1000",
            "8000, , 1000",
            "8000, 0, "
    })
    void shouldRejectInvalidInput(BigDecimal monthlyIncome, BigDecimal obligations, BigDecimal requestedLoanAmount) {
        assertThatThrownBy(() -> new EligibilityRequest(monthlyIncome, obligations, requestedLoanAmount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EligibilityResult check(String monthlyIncome, String obligations, String requestedLoanAmount) {
        return eligibilityService.checkEligibility(new EligibilityRequest(
                new BigDecimal(monthlyIncome),
                new BigDecimal(obligations),
                new BigDecimal(requestedLoanAmount)
        ));
    }

    private void assertNotEligible(EligibilityResult result, EligibilityReasonCode expectedReasonCode) {
        assertThat(result.decision()).isEqualTo(EligibilityDecision.NOT_ELIGIBLE);
        assertThat(result.reasonCode()).isEqualTo(expectedReasonCode);
    }
}
