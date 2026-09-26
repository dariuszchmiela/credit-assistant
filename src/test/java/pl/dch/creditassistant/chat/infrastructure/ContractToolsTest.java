package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.chat.application.ChatInvocationParameters;
import pl.dch.creditassistant.chat.application.ChatService;
import pl.dch.creditassistant.chat.application.CreditAssistant;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.contract.infrastructure.InMemoryContractRepository;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRecorder;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;
import pl.dch.creditassistant.privacy.application.PiiMasker;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-003 + FR-007: the tool resolves masked contract references internally and never returns raw contract numbers
 * to the LLM. Uses the real contract service; the invocation parameters are produced by the real {@link ChatService}.
 */
class ContractToolsTest {

    private final ContractTools contractTools =
            new ContractTools(new ContractStatusService(new InMemoryContractRepository()),
                    new ToolInvocationRecorder(invocation -> {
                    }));

    @Test
    void shouldResolvePlaceholderAndDescribeContractWithoutRawNumber() {
        InvocationParameters parameters = invocationParametersFor("What is the status of CTR-1001?");

        String result = contractTools.getContractStatus("[CONTRACT_NUMBER_1]", parameters);

        assertThat(result)
                .isEqualTo("contractReference=[CONTRACT_NUMBER_1], status=ACTIVE, outstandingPrincipal=85000.00, "
                        + "nextPaymentDate=2026-10-15")
                .doesNotContain("CTR-1001");
    }

    @Test
    void shouldFindExistingContractReferencedInLowerCase() {
        InvocationParameters parameters = invocationParametersFor("What is the status of ctr-1001?");

        String result = contractTools.getContractStatus("[CONTRACT_NUMBER_1]", parameters);

        assertThat(result)
                .isEqualTo("contractReference=[CONTRACT_NUMBER_1], status=ACTIVE, outstandingPrincipal=85000.00, "
                        + "nextPaymentDate=2026-10-15")
                .doesNotContainIgnoringCase("CTR-1001");
    }

    @Test
    void shouldMarkMissingNextPaymentOfPaidOffContract() {
        InvocationParameters parameters = invocationParametersFor("Compare CTR-1001 and CTR-1002.");

        String result = contractTools.getContractStatus("[CONTRACT_NUMBER_2]", parameters);

        assertThat(result)
                .isEqualTo("contractReference=[CONTRACT_NUMBER_2], status=PAID_OFF, outstandingPrincipal=0.00, "
                        + "nextPaymentDate=NONE")
                .doesNotContain("CTR-1002");
    }

    @Test
    void shouldReportUnknownRealContractAsNotFound() {
        InvocationParameters parameters = invocationParametersFor("Status of CTR-9999?");

        String result = contractTools.getContractStatus("[CONTRACT_NUMBER_1]", parameters);

        assertThat(result).isEqualTo("NOT_FOUND").doesNotContain("CTR-9999");
    }

    @Test
    void shouldRejectPlaceholderMissingFromInvocationContextInsteadOfQueryingAnotherContract() {
        InvocationParameters parameters = invocationParametersFor("Status of CTR-1001?");

        assertThat(contractTools.getContractStatus("[CONTRACT_NUMBER_2]", parameters))
                .isEqualTo("INVALID_CONTRACT_REFERENCE");
        assertThat(contractTools.getContractStatus("[PESEL_1]", parameters))
                .isEqualTo("INVALID_CONTRACT_REFERENCE");
        assertThat(contractTools.getContractStatus("[CONTRACT_NUMBER_1]", new InvocationParameters()))
                .isEqualTo("INVALID_CONTRACT_REFERENCE");
    }

    @Test
    void shouldNotResolveRawContractNumberGuessedByTheModel() {
        InvocationParameters parameters = invocationParametersFor("Status of CTR-1001?");

        assertThat(contractTools.getContractStatus("CTR-1001", parameters)).isEqualTo("INVALID_CONTRACT_REFERENCE");
    }

    @Test
    void shouldExposeOnlyTheContractReferenceInTheToolSchema() {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(ContractTools.class);

        assertThat(specifications).singleElement().satisfies(specification -> {
            assertThat(specification.name()).isEqualTo("getContractStatus");
            assertThat(specification.parameters().properties()).containsOnlyKeys("contractReference");
            assertThat(specification.parameters().required()).containsExactly("contractReference");
        });
    }

    /**
     * Masks the message with the production {@link ChatService} and captures the invocation parameters
     * it hands to the AI service.
     */
    private InvocationParameters invocationParametersFor(String rawMessage) {
        InvocationParameters[] captured = new InvocationParameters[1];
        CreditAssistant capturingAssistant = (maskedMessage, invocationParameters) -> {
            captured[0] = invocationParameters;
            return "";
        };

        new ChatService(new PiiMasker(), capturingAssistant, new AdvisorInteractionRecorder(interaction -> {
        })).chat(rawMessage);

        assertThat(ChatInvocationParameters.protectedValues(captured[0])).isNotNull();
        return captured[0];
    }
}
