package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.chat.application.ChatService;
import pl.dch.creditassistant.chat.application.CreditAssistant;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.contract.infrastructure.InMemoryContractRepository;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRecorder;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;
import pl.dch.creditassistant.privacy.application.PiiMasker;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code getContractStatus} is offered to the LLM only for chat requests whose advisor message contained a contract
 * number. The invocation parameters are produced by the real {@link ChatService} and masking.
 */
class ContractToolProviderTest {

    private final ContractToolProvider contractToolProvider = new ContractToolProvider(
            new ContractStatusService(new InMemoryContractRepository()),
            new ToolInvocationRecorder(invocation -> {
            }));

    @Test
    void shouldNotOfferContractToolWithoutContractNumberInTheMessage() {
        ToolProviderResult result = provideToolsFor(
                "Can a borrower make a partial early repayment and what happens to the remaining schedule?");

        assertThat(result.aiServiceTools()).isEmpty();
    }

    @Test
    void shouldNotOfferContractToolForOtherProtectedDataOnly() {
        ToolProviderResult result = provideToolsFor("Is the customer with PESEL 44051401458 eligible for a credit?");

        assertThat(result.aiServiceTools()).isEmpty();
    }

    @Test
    void shouldNotOfferContractToolWithoutPrivacyContext() {
        ToolProviderRequest request = ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().build())
                .userMessage(UserMessage.from("Status of [CONTRACT_NUMBER_1]?"))
                .build();

        assertThat(contractToolProvider.provideTools(request).aiServiceTools()).isEmpty();
    }

    @Test
    void shouldOfferExecutableContractToolWhenMessageContainsContractNumber() {
        InvocationParameters parameters = invocationParametersFor("What is the status of contract CTR-1001?");

        ToolProviderResult result = contractToolProvider.provideTools(requestFor(parameters));

        assertThat(result.aiServiceTools()).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("getContractStatus");
            assertThat(tool.toolSpecification().parameters().properties()).containsOnlyKeys("contractReference");
            assertThat(execute(tool, parameters)).isEqualTo("contractReference=[CONTRACT_NUMBER_1], status=ACTIVE, "
                    + "outstandingPrincipal=85000.00, nextPaymentDate=2026-10-15");
        });
    }

    private ToolProviderResult provideToolsFor(String rawMessage) {
        return contractToolProvider.provideTools(requestFor(invocationParametersFor(rawMessage)));
    }

    private static ToolProviderRequest requestFor(InvocationParameters parameters) {
        return ToolProviderRequest.builder()
                .invocationContext(InvocationContext.builder().invocationParameters(parameters).build())
                .userMessage(UserMessage.from("masked advisor message"))
                .build();
    }

    private static String execute(AiServiceTool tool, InvocationParameters parameters) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name(tool.name())
                .arguments("{\"contractReference\":\"[CONTRACT_NUMBER_1]\"}")
                .build();

        return tool.toolExecutor()
                .executeWithContext(request, InvocationContext.builder().invocationParameters(parameters).build())
                .resultText();
    }

    /**
     * Masks the message with the production {@link ChatService} and captures the invocation parameters
     * it hands to the AI service.
     */
    private static InvocationParameters invocationParametersFor(String rawMessage) {
        InvocationParameters[] captured = new InvocationParameters[1];
        CreditAssistant capturingAssistant = (maskedMessage, invocationParameters) -> {
            captured[0] = invocationParameters;
            return "";
        };

        new ChatService(new PiiMasker(), capturingAssistant, new AdvisorInteractionRecorder(interaction -> {
        })).chat(rawMessage);

        return captured[0];
    }
}
