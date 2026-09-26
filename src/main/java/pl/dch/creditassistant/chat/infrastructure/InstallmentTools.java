package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.chat.application.ChatInvocationParameters;
import pl.dch.creditassistant.credit.installment.application.InstallmentCalculator;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;

import java.math.BigDecimal;

@Component
public class InstallmentTools {

    private static final String TOOL_NAME = "calculateInstallment";

    private final InstallmentCalculator installmentCalculator;
    private final ToolInvocationRecorder toolInvocationRecorder;

    public InstallmentTools(InstallmentCalculator installmentCalculator, ToolInvocationRecorder toolInvocationRecorder) {
        this.installmentCalculator = installmentCalculator;
        this.toolInvocationRecorder = toolInvocationRecorder;
    }

    @Tool("Calculates the monthly installment and total repayment for a credit with equal monthly installments")
    public String calculateInstallment(
            @P("principal amount of the credit") BigDecimal principal,
            @P("annual interest rate in percent, for example 8.5") BigDecimal annualInterestRate,
            @P("repayment period in months") int months,
            InvocationParameters invocationParameters
    ) {
        return toolInvocationRecorder.execute(
                ChatInvocationParameters.interactionId(invocationParameters),
                TOOL_NAME,
                () -> calculate(principal, annualInterestRate, months)
        );
    }

    private String calculate(BigDecimal principal, BigDecimal annualInterestRate, int months) {
        InstallmentCalculation calculation = installmentCalculator.calculate(
                principal,
                annualInterestRate,
                months
        );

        return "monthlyInstallment=" + calculation.monthlyInstallment().toPlainString()
                + ", totalRepayment=" + calculation.totalRepayment().toPlainString();
    }
}
