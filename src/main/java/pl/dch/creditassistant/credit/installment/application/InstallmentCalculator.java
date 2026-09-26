package pl.dch.creditassistant.credit.installment.application;

import org.springframework.stereotype.Service;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * FR-004, SPEC 11.2: equal monthly installments (annuity). Validates its input, so every adapter gets
 * the same deterministic errors.
 */
@Service
public class InstallmentCalculator {

    private static final int MONEY_SCALE = 2;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    public InstallmentCalculation calculate(
            BigDecimal principal,
            BigDecimal annualInterestRate,
            int months
    ) {
        validate(principal, annualInterestRate, months);

        BigDecimal monthlyRate = annualInterestRate
                .divide(PERCENT, MATH_CONTEXT)
                .divide(MONTHS_PER_YEAR, MATH_CONTEXT);

        BigDecimal monthlyInstallment = monthlyRate.signum() == 0
                ? principal.divide(BigDecimal.valueOf(months), MONEY_SCALE, RoundingMode.HALF_UP)
                : annuityInstallment(principal, monthlyRate, months);

        BigDecimal totalRepayment = monthlyInstallment
                .multiply(BigDecimal.valueOf(months))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new InstallmentCalculation(
                monthlyInstallment,
                totalRepayment
        );
    }

    private BigDecimal annuityInstallment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        BigDecimal onePlusRatePower = BigDecimal.ONE
                .add(monthlyRate)
                .pow(months, MATH_CONTEXT);

        return principal
                .multiply(monthlyRate, MATH_CONTEXT)
                .multiply(onePlusRatePower, MATH_CONTEXT)
                .divide(onePlusRatePower.subtract(BigDecimal.ONE), MATH_CONTEXT)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void validate(BigDecimal principal, BigDecimal annualInterestRate, int months) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("principal must be greater than zero");
        }
        if (annualInterestRate == null || annualInterestRate.signum() < 0) {
            throw new IllegalArgumentException("annualInterestRate must not be negative");
        }
        if (months <= 0) {
            throw new IllegalArgumentException("months must be greater than zero");
        }
    }
}
