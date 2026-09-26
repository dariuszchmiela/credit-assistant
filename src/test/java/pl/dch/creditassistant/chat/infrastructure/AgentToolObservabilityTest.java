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
import pl.dch.creditassistant.credit.eligibility.application.EligibilityService;
import pl.dch.creditassistant.credit.installment.application.InstallmentCalculator;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRecorder;
import pl.dch.creditassistant.observability.application.ToolInvocationRecorder;
import pl.dch.creditassistant.observability.domain.ToolInvocation;
import pl.dch.creditassistant.observability.domain.ToolInvocationStatus;
import pl.dch.creditassistant.privacy.application.PiiMasker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SPEC 54 / 63: every actual execution of a LangChain4j agent tool is recorded as a {@link ToolInvocation} of the
 * current advisor interaction, and {@link InvocationParameters} stay invisible in the tool schemas.
 * Real credit services and an in-memory tool invocation repository; no LLM, no database.
 */
class AgentToolObservabilityTest {

    private final List<ToolInvocation> recordedInvocations = new ArrayList<>();
    private final ToolInvocationRecorder toolInvocationRecorder = new ToolInvocationRecorder(recordedInvocations::add);

    private final ContractTools contractTools = new ContractTools(
            new ContractStatusService(new InMemoryContractRepository()), toolInvocationRecorder);
    private final InstallmentTools installmentTools = new InstallmentTools(
            new InstallmentCalculator(), toolInvocationRecorder);
    private final EligibilityTools eligibilityTools = new EligibilityTools(
            new EligibilityService(), toolInvocationRecorder);

    @Test
    void shouldRecordContractToolExecution() {
        InvocationParameters parameters = invocationParametersFor("Status of CTR-1001?");

        String result = contractTools.getContractStatus("[CONTRACT_NUMBER_1]", parameters);

        assertThat(result).contains("status=ACTIVE");
        assertSingleInvocation("getContractStatus", parameters, ToolInvocationStatus.SUCCESS);
    }

    @Test
    void shouldRecordInstallmentToolExecution() {
        InvocationParameters parameters = invocationParametersFor("Calculate an installment");

        String result = installmentTools.calculateInstallment(
                new BigDecimal("100000"), new BigDecimal("8.5"), 60, parameters);

        assertThat(result).isEqualTo("monthlyInstallment=2051.65, totalRepayment=123099.00");
        assertSingleInvocation("calculateInstallment", parameters, ToolInvocationStatus.SUCCESS);
    }

    @Test
    void shouldRecordEligibilityToolExecution() {
        InvocationParameters parameters = invocationParametersFor("Is the customer eligible?");

        String result = eligibilityTools.checkEligibility(
                new BigDecimal("8000"), new BigDecimal("2000"), new BigDecimal("40000"), parameters);

        assertThat(result).startsWith("decision=ELIGIBLE, reasonCode=ELIGIBLE");
        assertSingleInvocation("checkEligibility", parameters, ToolInvocationStatus.SUCCESS);
    }

    @Test
    void shouldRecordFailedToolExecutionAndRethrowTheBusinessError() {
        InvocationParameters parameters = invocationParametersFor("Calculate an installment");

        assertThatThrownBy(() -> installmentTools.calculateInstallment(
                new BigDecimal("100000"), new BigDecimal("8.5"), 0, parameters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("months must be greater than zero");

        assertSingleInvocation("calculateInstallment", parameters, ToolInvocationStatus.ERROR);
        assertThat(recordedInvocations.getFirst().toString()).doesNotContain("100000").doesNotContain("8.5");
    }

    @Test
    void shouldExecuteToolWithoutRecordingWhenInteractionContextIsMissing() {
        String result = installmentTools.calculateInstallment(
                new BigDecimal("100000"), new BigDecimal("8.5"), 60, new InvocationParameters());

        assertThat(result).isEqualTo("monthlyInstallment=2051.65, totalRepayment=123099.00");
        assertThat(recordedInvocations).isEmpty();
    }

    @Test
    void shouldExposeOnlyBusinessParametersInToolSchemas() {
        assertThat(onlyToolParameters(ContractTools.class)).containsExactly("contractReference");
        assertThat(onlyToolParameters(InstallmentTools.class))
                .containsExactly("principal", "annualInterestRate", "months");
        assertThat(onlyToolParameters(EligibilityTools.class))
                .containsExactly("monthlyIncome", "existingMonthlyObligations", "requestedLoanAmount");
    }

    private void assertSingleInvocation(String toolName, InvocationParameters parameters, ToolInvocationStatus status) {
        UUID interactionId = ChatInvocationParameters.interactionId(parameters);

        assertThat(recordedInvocations).singleElement().satisfies(invocation -> {
            assertThat(invocation.toolName()).isEqualTo(toolName);
            assertThat(invocation.advisorInteractionId()).isEqualTo(interactionId);
            assertThat(invocation.status()).isEqualTo(status);
            assertThat(invocation.durationMillis()).isNotNegative();
        });
    }

    private List<String> onlyToolParameters(Class<?> toolsClass) {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(toolsClass);
        assertThat(specifications).hasSize(1);

        return List.copyOf(specifications.getFirst().parameters().properties().keySet());
    }

    /**
     * Invocation parameters as produced by the production {@link ChatService} for an advisor request.
     */
    private InvocationParameters invocationParametersFor(String rawMessage) {
        InvocationParameters[] captured = new InvocationParameters[1];
        CreditAssistant capturingAssistant = (maskedMessage, invocationParameters) -> {
            captured[0] = invocationParameters;
            return "";
        };
        new ChatService(new PiiMasker(), capturingAssistant, new AdvisorInteractionRecorder(interaction -> {
        })).chat(rawMessage);

        assertThat(ChatInvocationParameters.interactionId(captured[0])).isNotNull();
        return captured[0];
    }
}
