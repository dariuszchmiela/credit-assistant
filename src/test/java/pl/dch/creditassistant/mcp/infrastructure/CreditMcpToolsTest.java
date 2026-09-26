package pl.dch.creditassistant.mcp.infrastructure;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.contract.infrastructure.InMemoryContractRepository;
import pl.dch.creditassistant.credit.eligibility.application.EligibilityService;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityDecision;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityReasonCode;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityRequest;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityResult;
import pl.dch.creditassistant.credit.installment.application.InstallmentCalculator;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-006: the MCP adapter is only another entry point to the same credit services.
 * Uses the real services; no Spring context, MCP transport or LLM.
 */
class CreditMcpToolsTest {

    private final InstallmentCalculator installmentCalculator = new InstallmentCalculator();
    private final EligibilityService eligibilityService = new EligibilityService();
    private final CreditMcpTools creditMcpTools = new CreditMcpTools(
            new ContractStatusService(new InMemoryContractRepository()),
            installmentCalculator,
            eligibilityService
    );

    @Test
    void shouldReturnDeterministicContractData() {
        CallToolResult result = creditMcpTools.getContractStatus(Map.of("contractNumber", "CTR-1001"));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.structuredContent()).isEqualTo(new ContractStatusResult(
                "CTR-1001", true, "ACTIVE", new BigDecimal("85000.00"), "2026-10-15"));
    }

    @Test
    void shouldReportExplicitlyMissingNextPayment() {
        CallToolResult result = creditMcpTools.getContractStatus(Map.of("contractNumber", "CTR-1002"));

        assertThat(result.structuredContent()).isEqualTo(new ContractStatusResult(
                "CTR-1002", true, "PAID_OFF", new BigDecimal("0.00"), null));
    }

    @Test
    void shouldReportUnknownContractAsNotFound() {
        CallToolResult result = creditMcpTools.getContractStatus(Map.of("contractNumber", "CTR-9999"));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.structuredContent()).isEqualTo(new ContractStatusResult("CTR-9999", false, null, null, null));
    }

    @Test
    void shouldReturnInstallmentCalculatedByTheSharedCalculator() {
        CallToolResult result = creditMcpTools.calculateInstallment(
                Map.of("principal", 100000, "annualInterestRate", 8.5, "months", 60));

        InstallmentCalculation calculation = (InstallmentCalculation) result.structuredContent();
        assertThat(calculation.monthlyInstallment()).isEqualByComparingTo("2051.65");
        assertThat(calculation.totalRepayment()).isEqualByComparingTo("123099.00");
        assertThat(calculation).isEqualTo(installmentCalculator.calculate(
                new BigDecimal("100000"), new BigDecimal("8.5"), 60));
    }

    @Test
    void shouldReturnEligibleDecisionOfTheSharedEligibilityService() {
        CallToolResult result = creditMcpTools.checkEligibility(
                Map.of("monthlyIncome", 8000, "existingMonthlyObligations", 2000, "requestedLoanAmount", 40000));

        EligibilityResult eligibility = (EligibilityResult) result.structuredContent();
        assertThat(eligibility.decision()).isEqualTo(EligibilityDecision.ELIGIBLE);
        assertThat(eligibility.reasonCode()).isEqualTo(EligibilityReasonCode.ELIGIBLE);
        assertThat(eligibility).isEqualTo(eligibilityService.checkEligibility(new EligibilityRequest(
                new BigDecimal("8000"), new BigDecimal("2000"), new BigDecimal("40000"))));
    }

    @Test
    void shouldReturnRejectedDecisionOfTheSharedEligibilityService() {
        CallToolResult result = creditMcpTools.checkEligibility(
                Map.of("monthlyIncome", 8000, "existingMonthlyObligations", 5000, "requestedLoanAmount", 20000));

        EligibilityResult eligibility = (EligibilityResult) result.structuredContent();
        assertThat(eligibility.decision()).isEqualTo(EligibilityDecision.NOT_ELIGIBLE);
        assertThat(eligibility.reasonCode()).isEqualTo(EligibilityReasonCode.OBLIGATIONS_TOO_HIGH);
        assertThat(eligibility).isEqualTo(eligibilityService.checkEligibility(new EligibilityRequest(
                new BigDecimal("8000"), new BigDecimal("5000"), new BigDecimal("20000"))));
    }

    @Test
    void shouldReportInvalidEligibilityInputAsToolError() {
        CallToolResult result = creditMcpTools.toolSpecifications(McpJsonDefaults.getMapper())
                .stream()
                .filter(specification -> specification.tool().name().equals(CreditMcpTools.CHECK_ELIGIBILITY))
                .findFirst()
                .orElseThrow()
                .callHandler()
                .apply(null, new CallToolRequest(
                        CreditMcpTools.CHECK_ELIGIBILITY,
                        Map.of("monthlyIncome", 0, "existingMonthlyObligations", 0, "requestedLoanAmount", 1000),
                        null));

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).singleElement()
                .isInstanceOfSatisfying(TextContent.class, text -> assertThat(text.text()).contains("monthlyIncome"));
    }
}
