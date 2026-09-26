package pl.dch.creditassistant.credit.installment.application;

import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

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
}