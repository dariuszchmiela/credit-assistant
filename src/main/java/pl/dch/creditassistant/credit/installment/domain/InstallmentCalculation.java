package pl.dch.creditassistant.credit.installment.domain;

import java.math.BigDecimal;

public record InstallmentCalculation(
        BigDecimal monthlyInstallment,
        BigDecimal totalRepayment
) {
}