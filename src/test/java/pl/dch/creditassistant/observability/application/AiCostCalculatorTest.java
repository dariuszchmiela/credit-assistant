package pl.dch.creditassistant.observability.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-008, SPEC 57/59: deterministic BigDecimal cost estimate from token usage and prices per million tokens.
 */
class AiCostCalculatorTest {

    @Test
    void shouldEstimateExactZeroWithZeroPrices() {
        AiCostCalculator calculator = new AiCostCalculator(BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(calculator.estimate(1_500, 300)).hasValueSatisfying(cost -> {
            assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(cost.signum()).isZero();
        });
    }

    @Test
    void shouldEstimateExactCostFromPricesPerMillionTokens() {
        AiCostCalculator calculator = new AiCostCalculator(new BigDecimal("2"), new BigDecimal("10"));

        // 1000 * 2 / 1e6 = 0.002; 200 * 10 / 1e6 = 0.002
        assertThat(calculator.estimate(1_000, 200)).hasValue(new BigDecimal("0.0040000000"));
    }

    @Test
    void shouldPriceInputAndOutputTokensIndependently() {
        AiCostCalculator inputOnly = new AiCostCalculator(new BigDecimal("3"), BigDecimal.ZERO);
        AiCostCalculator outputOnly = new AiCostCalculator(BigDecimal.ZERO, new BigDecimal("15"));

        assertThat(inputOnly.estimate(1_000_000, 1_000_000)).hasValue(new BigDecimal("3.0000000000"));
        assertThat(outputOnly.estimate(1_000_000, 1_000_000)).hasValue(new BigDecimal("15.0000000000"));
    }

    @Test
    void shouldKeepDecimalPrecisionWithoutFloatingPointErrors() {
        AiCostCalculator calculator = new AiCostCalculator(new BigDecimal("0.1"), new BigDecimal("0.2"));

        // with doubles 0.1 + 0.2 would not be exactly 0.3
        assertThat(calculator.estimate(1_000_000, 1_000_000)).hasValue(new BigDecimal("0.3000000000"));
        assertThat(calculator.estimate(1, 1)).hasValue(new BigDecimal("0.0000003000"));
    }

    @Test
    void shouldReturnUnknownCostWhenTokenUsageIsUnknown() {
        AiCostCalculator calculator = new AiCostCalculator(new BigDecimal("2"), new BigDecimal("10"));

        assertThat(calculator.estimate(null, 200)).isEmpty();
        assertThat(calculator.estimate(1_000, null)).isEmpty();
    }

    @Test
    void shouldRejectNegativePrices() {
        assertThatThrownBy(() -> new AiCostCalculator(new BigDecimal("-0.01"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inputPricePerMillionTokens must not be negative");
        assertThatThrownBy(() -> new AiCostCalculator(BigDecimal.ZERO, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outputPricePerMillionTokens must not be negative");
    }
}
