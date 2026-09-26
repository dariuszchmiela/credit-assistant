package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.credit.eligibility.application.EligibilityService;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityRequest;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityResult;

import java.math.BigDecimal;

@Component
public class EligibilityTools {

    private final EligibilityService eligibilityService;

    public EligibilityTools(EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @Tool("Checks whether a customer is eligible for a credit using the bank's deterministic eligibility policy. "
            + "Returns the authoritative eligibility decision, a machine-readable reason code and an explanation")
    public String checkEligibility(
            @P("customer's monthly income") BigDecimal monthlyIncome,
            @P("customer's total existing monthly obligations such as other loan installments; 0 if none") BigDecimal existingMonthlyObligations,
            @P("requested loan amount") BigDecimal requestedLoanAmount
    ) {
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
