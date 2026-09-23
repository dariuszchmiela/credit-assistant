package pl.dch.creditassistant.credit.installment.application;

import org.springframework.stereotype.Service;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Service
public class InstallmentCalculator {

    private static final int MONEY_SCALE = 2;
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL64;

    public InstallmentCalculation calculate(
            BigDecimal principal,
            BigDecimal annualInterestRate,
            int months
    ) {
        BigDecimal monthlyRate = annualInterestRate
                .divide(BigDecimal.valueOf(100), MATH_CONTEXT)
                .divide(BigDecimal.valueOf(12), MATH_CONTEXT);

        BigDecimal onePlusRatePower = BigDecimal.ONE
                .add(monthlyRate)
                .pow(months, MATH_CONTEXT);

        BigDecimal monthlyInstallment = principal
                .multiply(monthlyRate, MATH_CONTEXT)
                .multiply(onePlusRatePower, MATH_CONTEXT)
                .divide(onePlusRatePower.subtract(BigDecimal.ONE), MATH_CONTEXT)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalRepayment = monthlyInstallment
                .multiply(BigDecimal.valueOf(months))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new InstallmentCalculation(
                monthlyInstallment,
                totalRepayment
        );
    }
}