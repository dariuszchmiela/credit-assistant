package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.chat.application.ChatInvocationParameters;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;
import pl.dch.creditassistant.privacy.domain.PiiCategory;

/**
 * Per-request exposure of {@code getContractStatus}: the single {@link ToolProvider} bean is picked up automatically
 * by {@code @AiService} and consulted once per chat invocation, next to the static installment and eligibility tools.
 * <p>
 * The contract tool is offered to the LLM only when the advisor message contained a contract number, i.e. the
 * internal {@link pl.dch.creditassistant.privacy.domain.ProtectedValues} can resolve a contract placeholder.
 * Without one the tool could only return {@code INVALID_CONTRACT_REFERENCE}, so the model is not given the option.
 */
@Component
class ContractToolProvider implements ToolProvider {

    private final ToolProviderResult contractTools;

    ContractToolProvider(ContractStatusService contractStatusService, ToolInvocationRecorder toolInvocationRecorder) {
        // Same specification and executor as a statically wired @Tool bean.
        this.contractTools = new ToolProviderResult(ToolService.findTools(
                new ContractTools(contractStatusService, toolInvocationRecorder)));
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        boolean contractReferenced = ChatInvocationParameters.protectedValues(request.invocationParameters())
                .containsAny(PiiCategory.CONTRACT_NUMBER);

        return contractReferenced ? contractTools : ToolProviderResult.builder().build();
    }
}
