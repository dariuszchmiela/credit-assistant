package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.chat.application.ChatInvocationParameters;
import pl.dch.creditassistant.credit.eligibility.application.EligibilityService;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityRequest;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityResult;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;

import java.math.BigDecimal;

@Component
public class EligibilityTools {

    private static final String TOOL_NAME = "checkEligibility";

    private final EligibilityService eligibilityService;
    private final ToolInvocationRecorder toolInvocationRecorder;

    public EligibilityTools(EligibilityService eligibilityService, ToolInvocationRecorder toolInvocationRecorder) {
        this.eligibilityService = eligibilityService;
        this.toolInvocationRecorder = toolInvocationRecorder;
    }

    @Tool("Checks whether a customer is eligible for a credit using the bank's deterministic eligibility policy. "
            + "Returns the authoritative eligibility decision, a machine-readable reason code and an explanation")
    public String checkEligibility(
            @P("customer's monthly income") BigDecimal monthlyIncome,
            @P("customer's total existing monthly obligations such as other loan installments; 0 if none") BigDecimal existingMonthlyObligations,
            @P("requested loan amount") BigDecimal requestedLoanAmount,
            InvocationParameters invocationParameters
    ) {
        return toolInvocationRecorder.execute(
                ChatInvocationParameters.interactionId(invocationParameters),
                TOOL_NAME,
                () -> check(monthlyIncome, existingMonthlyObligations, requestedLoanAmount)
        );
    }

    private String check(BigDecimal monthlyIncome, BigDecimal existingMonthlyObligations, BigDecimal requestedLoanAmount) {
        EligibilityResult result = eligibilityService.checkEligibility(new EligibilityRequest(
                monthlyIncome,
                existingMonthlyObligations,
                requestedLoanAmount
        ));

        return "decision=" + result.decision().name()
                + ", reasonCode=" + result.reasonCode().name()
                + ", explanation=" + result.explanation();
    }
}
