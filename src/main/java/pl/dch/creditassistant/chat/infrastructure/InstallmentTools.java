package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.credit.installment.application.InstallmentCalculator;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;

@Component
public class InstallmentTools {

    private final InstallmentCalculator installmentCalculator;

    public InstallmentTools(InstallmentCalculator installmentCalculator) {
        this.installmentCalculator = installmentCalculator;
    }

    @Tool("Calculates the monthly installment and total repayment for a credit with equal monthly installments")
    public InstallmentCalculation calculateInstallment(
            @P("principal amount of the credit") BigDecimal principal,
            @P("annual interest rate in percent, for example 8.5") BigDecimal annualInterestRate,
            @P("repayment period in months") int months
    ) {
        return installmentCalculator.calculate(
                principal,
                annualInterestRate,
                months
        );
    }
}