package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.contract.domain.CreditContract;

import java.time.LocalDate;

@Component
public class ContractTools {

    private static final String CONTRACT_NOT_FOUND = "NOT_FOUND";
    private static final String NO_NEXT_PAYMENT = "NONE";

    private final ContractStatusService contractStatusService;

    public ContractTools(ContractStatusService contractStatusService) {
        this.contractStatusService = contractStatusService;
    }

    @Tool("Returns the current status, outstanding principal and next payment date of a credit contract "
            + "identified by its contract number")
    public String getContractStatus(
            @P("credit contract number, for example CTR-1001") String contractNumber
    ) {
        return contractStatusService.findContract(contractNumber)
                .map(this::describe)
                .orElse(CONTRACT_NOT_FOUND);
    }

    private String describe(CreditContract contract) {
        String nextPaymentDate = contract.nextPaymentDate()
                .map(LocalDate::toString)
                .orElse(NO_NEXT_PAYMENT);

        return "contractNumber=" + contract.contractNumber()
                + ", status=" + contract.status().name()
                + ", outstandingPrincipal=" + contract.outstandingPrincipal().toPlainString()
                + ", nextPaymentDate=" + nextPaymentDate;
    }
}
