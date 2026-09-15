package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;

@Component
public class ContractTools {

    private static final String CONTRACT_NOT_FOUND = "NOT_FOUND";

    private final ContractStatusService contractStatusService;

    public ContractTools(ContractStatusService contractStatusService) {
        this.contractStatusService = contractStatusService;
    }

    @Tool("Returns the current status of a credit contract identified by its contract number")
    public String getContractStatus(
            @P("credit contract number, for example CTR-1001") String contractNumber
    ) {
        return contractStatusService.getContractStatus(contractNumber)
                .map(Enum::name)
                .orElse(CONTRACT_NOT_FOUND);
    }
}