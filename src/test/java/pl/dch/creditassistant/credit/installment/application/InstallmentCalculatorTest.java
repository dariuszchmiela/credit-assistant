package pl.dch.creditassistant.credit.installment.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstallmentCalculatorTest {

    private final InstallmentCalculator installmentCalculator = new InstallmentCalculator();

    @Test
    void shouldCalculateEqualMonthlyInstallment() {
        InstallmentCalculation result = installmentCalculator.calculate(
                new BigDecimal("100000"),
                new BigDecimal("8.5"),
                60
        );

        assertThat(result.monthlyInstallment())
                .isEqualByComparingTo("2051.65");

        assertThat(result.totalRepayment())
                .isEqualByComparingTo("123099.00");
    }

    @Test
    void shouldSplitPrincipalEquallyWhenInterestRateIsZero() {
        InstallmentCalculation result = installmentCalculator.calculate(
                new BigDecimal("12000"),
                BigDecimal.ZERO,
                12
        );

        assertThat(result.monthlyInstallment()).isEqualByComparingTo("1000.00");
        assertThat(result.totalRepayment()).isEqualByComparingTo("12000.00");
    }

    @Test
    void shouldRoundZeroInterestInstallmentLikeAnnuityInstallment() {
        InstallmentCalculation result = installmentCalculator.calculate(
                new BigDecimal("10000"),
                new BigDecimal("0.00"),
                3
        );

        assertThat(result.monthlyInstallment()).isEqualByComparingTo("3333.33");
        assertThat(result.totalRepayment()).isEqualByComparingTo("9999.99");
    }

    @Test
    void shouldAcceptSmallestValidInput() {
        InstallmentCalculation result = installmentCalculator.calculate(
                new BigDecimal("0.01"),
                BigDecimal.ZERO,
                1
        );

        assertThat(result.monthlyInstallment()).isEqualByComparingTo("0.01");
    }

    @ParameterizedTest(name = "principal={0}, rate={1}, months={2}")
    @CsvSource({
            "0, 8.5, 60, principal must be greater than zero",
            "-1, 8.5, 60, principal must be greater than zero",
            ", 8.5, 60, principal must be greater than zero",
            "100000, -0.01, 60, annualInterestRate must not be negative",
            "100000, , 60, annualInterestRate must not be negative",
            "100000, 8.5, 0, months must be greater than zero",
            "100000, 8.5, -12, months must be greater than zero"
    })
    void shouldRejectInvalidInput(BigDecimal principal, BigDecimal annualInterestRate, int months, String message) {
        assertThatThrownBy(() -> installmentCalculator.calculate(principal, annualInterestRate, months))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }
}