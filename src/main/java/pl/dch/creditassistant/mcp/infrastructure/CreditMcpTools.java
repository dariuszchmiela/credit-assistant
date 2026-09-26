package pl.dch.creditassistant.mcp.infrastructure;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.eligibility.application.EligibilityService;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityRequest;
import pl.dch.creditassistant.credit.eligibility.domain.EligibilityResult;
import pl.dch.creditassistant.credit.installment.application.InstallmentCalculator;
import pl.dch.creditassistant.credit.installment.domain.InstallmentCalculation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * FR-006: MCP adapter over the credit application services.
 * Maps MCP arguments, delegates to the same services as the LangChain4j tools and returns their results
 * as structured content. No business rules live here.
 */
@Component
class CreditMcpTools {

    static final String GET_CONTRACT_STATUS = "getContractStatus";
    static final String CALCULATE_INSTALLMENT = "calculateInstallment";
    static final String CHECK_ELIGIBILITY = "checkEligibility";

    private static final String CONTRACT_NUMBER = "contractNumber";
    private static final String PRINCIPAL = "principal";
    private static final String ANNUAL_INTEREST_RATE = "annualInterestRate";
    private static final String MONTHS = "months";
    private static final String MONTHLY_INCOME = "monthlyIncome";
    private static final String EXISTING_MONTHLY_OBLIGATIONS = "existingMonthlyObligations";
    private static final String REQUESTED_LOAN_AMOUNT = "requestedLoanAmount";

    private static final String CONTRACT_STATUS_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "contractNumber": { "type": "string", "description": "credit contract number, for example CTR-1001" }
              },
              "required": ["contractNumber"]
            }
            """;

    private static final String CONTRACT_STATUS_OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "contractNumber": { "type": "string" },
                "found": { "type": "boolean", "description": "false when no contract with this number exists" },
                "status": { "type": ["string", "null"], "description": "ACTIVE, PAID_OFF, OVERDUE or CANCELLED" },
                "outstandingPrincipal": { "type": ["number", "null"] },
                "nextPaymentDate": { "type": ["string", "null"], "description": "ISO date; null when there is no next payment" }
              },
              "required": ["contractNumber", "found", "status", "outstandingPrincipal", "nextPaymentDate"]
            }
            """;

    private static final String INSTALLMENT_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "principal": { "type": "number", "description": "principal amount of the credit" },
                "annualInterestRate": { "type": "number", "description": "annual interest rate in percent, for example 8.5" },
                "months": { "type": "integer", "description": "repayment period in months" }
              },
              "required": ["principal", "annualInterestRate", "months"]
            }
            """;

    private static final String INSTALLMENT_OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "monthlyInstallment": { "type": "number" },
                "totalRepayment": { "type": "number" }
              },
              "required": ["monthlyInstallment", "totalRepayment"]
            }
            """;

    private static final String ELIGIBILITY_INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "monthlyIncome": { "type": "number", "description": "customer's monthly income" },
                "existingMonthlyObligations": { "type": "number", "description": "total existing monthly obligations; 0 if none" },
                "requestedLoanAmount": { "type": "number", "description": "requested loan amount" }
              },
              "required": ["monthlyIncome", "existingMonthlyObligations", "requestedLoanAmount"]
            }
            """;

    private static final String ELIGIBILITY_OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "decision": { "type": "string", "description": "ELIGIBLE or NOT_ELIGIBLE" },
                "reasonCode": { "type": "string", "description": "machine-readable reason code" },
                "explanation": { "type": "string" }
              },
              "required": ["decision", "reasonCode", "explanation"]
            }
            """;

    private final ContractStatusService contractStatusService;
    private final InstallmentCalculator installmentCalculator;
    private final EligibilityService eligibilityService;

    CreditMcpTools(
            ContractStatusService contractStatusService,
            InstallmentCalculator installmentCalculator,
            EligibilityService eligibilityService
    ) {
        this.contractStatusService = contractStatusService;
        this.installmentCalculator = installmentCalculator;
        this.eligibilityService = eligibilityService;
    }

    List<SyncToolSpecification> toolSpecifications(McpJsonMapper jsonMapper) {
        return List.of(
                toolSpecification(jsonMapper, GET_CONTRACT_STATUS,
                        "Returns the current status, outstanding principal and next payment date of a credit contract",
                        CONTRACT_STATUS_INPUT_SCHEMA, CONTRACT_STATUS_OUTPUT_SCHEMA, this::getContractStatus),
                toolSpecification(jsonMapper, CALCULATE_INSTALLMENT,
                        "Calculates the monthly installment and total repayment for a credit with equal monthly installments",
                        INSTALLMENT_INPUT_SCHEMA, INSTALLMENT_OUTPUT_SCHEMA, this::calculateInstallment),
                toolSpecification(jsonMapper, CHECK_ELIGIBILITY,
                        "Checks credit eligibility using the bank's deterministic eligibility policy and returns the "
                                + "authoritative decision, reason code and explanation",
                        ELIGIBILITY_INPUT_SCHEMA, ELIGIBILITY_OUTPUT_SCHEMA, this::checkEligibility)
        );
    }

    CallToolResult getContractStatus(Map<String, Object> arguments) {
        String contractNumber = (String) arguments.get(CONTRACT_NUMBER);

        ContractStatusResult result = contractStatusService.findContract(contractNumber)
                .map(ContractStatusResult::of)
                .orElseGet(() -> ContractStatusResult.notFound(contractNumber));

        return structured(result);
    }

    CallToolResult calculateInstallment(Map<String, Object> arguments) {
        InstallmentCalculation calculation = installmentCalculator.calculate(
                decimalArgument(arguments, PRINCIPAL),
                decimalArgument(arguments, ANNUAL_INTEREST_RATE),
                ((Number) arguments.get(MONTHS)).intValue()
        );

        return structured(calculation);
    }

    CallToolResult checkEligibility(Map<String, Object> arguments) {
        EligibilityResult result = eligibilityService.checkEligibility(new EligibilityRequest(
                decimalArgument(arguments, MONTHLY_INCOME),
                decimalArgument(arguments, EXISTING_MONTHLY_OBLIGATIONS),
                decimalArgument(arguments, REQUESTED_LOAN_AMOUNT)
        ));

        return structured(result);
    }

    private SyncToolSpecification toolSpecification(
            McpJsonMapper jsonMapper,
            String name,
            String description,
            String inputSchema,
            String outputSchema,
            Function<Map<String, Object>, CallToolResult> handler
    ) {
        Tool tool = Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(jsonMapper, inputSchema)
                .outputSchema(jsonMapper, outputSchema)
                .build();

        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> handleInvalidInput(handler, request.arguments()))
                .build();
    }

    private CallToolResult handleInvalidInput(
            Function<Map<String, Object>, CallToolResult> handler,
            Map<String, Object> arguments
    ) {
        try {
            return handler.apply(arguments);
        } catch (IllegalArgumentException exception) {
            return CallToolResult.builder()
                    .addTextContent(exception.getMessage())
                    .isError(true)
                    .build();
        }
    }

    private CallToolResult structured(Object result) {
        return CallToolResult.builder()
                .structuredContent(result)
                .build();
    }

    /**
     * JSON numbers arrive as Integer, Long or Double; their decimal text is converted exactly to BigDecimal.
     */
    private BigDecimal decimalArgument(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);

        return value == null ? null : new BigDecimal(value.toString());
    }
}
