package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.chat.application.ChatInvocationParameters;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.contract.domain.CreditContract;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;
import pl.dch.creditassistant.privacy.domain.PiiCategory;

import java.time.LocalDate;
import java.util.Optional;

/**
 * FR-003 tool with FR-007 masking: the LLM only knows contract placeholders such as {@code [CONTRACT_NUMBER_1]}.
 * The placeholder is resolved to the original contract number inside Java, and the result sent back to the LLM
 * identifies the contract by the same placeholder, never by its number.
 */
@Component
public class ContractTools {

    private static final String TOOL_NAME = "getContractStatus";
    private static final String CONTRACT_NOT_FOUND = "NOT_FOUND";
    private static final String INVALID_CONTRACT_REFERENCE = "INVALID_CONTRACT_REFERENCE";
    private static final String NO_NEXT_PAYMENT = "NONE";

    private final ContractStatusService contractStatusService;
    private final ToolInvocationRecorder toolInvocationRecorder;

    public ContractTools(ContractStatusService contractStatusService, ToolInvocationRecorder toolInvocationRecorder) {
        this.contractStatusService = contractStatusService;
        this.toolInvocationRecorder = toolInvocationRecorder;
    }

    @Tool("Returns the current status, outstanding principal and next payment date of a credit contract "
            + "identified by its protected contract reference")
    public String getContractStatus(
            @P("protected contract reference exactly as it appears in the message, for example [CONTRACT_NUMBER_1]")
            String contractReference,
            InvocationParameters invocationParameters
    ) {
        return toolInvocationRecorder.execute(
                ChatInvocationParameters.interactionId(invocationParameters),
                TOOL_NAME,
                () -> findContract(contractReference, invocationParameters)
        );
    }

    private String findContract(String contractReference, InvocationParameters invocationParameters) {
        Optional<String> contractNumber = ChatInvocationParameters.protectedValues(invocationParameters)
                .originalOf(PiiCategory.CONTRACT_NUMBER, String.valueOf(contractReference).strip());

        if (contractNumber.isEmpty()) {
            return INVALID_CONTRACT_REFERENCE;
        }

        return contractStatusService.findContract(contractNumber.get())
                .map(contract -> describe(contractReference.strip(), contract))
                .orElse(CONTRACT_NOT_FOUND);
    }

    private String describe(String contractReference, CreditContract contract) {
        String nextPaymentDate = contract.nextPaymentDate()
                .map(LocalDate::toString)
                .orElse(NO_NEXT_PAYMENT);

        return "contractReference=" + contractReference
                + ", status=" + contract.status().name()
                + ", outstandingPrincipal=" + contract.outstandingPrincipal().toPlainString()
                + ", nextPaymentDate=" + nextPaymentDate;
    }
}
