package pl.dch.creditassistant.evaluation;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.chat.application.CreditAssistant;
import pl.dch.creditassistant.chat.infrastructure.ContractTools;
import pl.dch.creditassistant.chat.infrastructure.EligibilityTools;
import pl.dch.creditassistant.chat.infrastructure.InstallmentTools;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocument;
import pl.dch.creditassistant.knowledge.infrastructure.BundledKnowledgeDocuments;
import pl.dch.creditassistant.privacy.application.PiiMasker;
import pl.dch.creditassistant.privacy.domain.PiiCategory;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * FR-009: validates the evaluation set itself. Runs in every build; needs no LLM, no database.
 */
class EvaluationDatasetTest {

    private static final int MINIMUM_CASES = 10;

    private final EvaluationDataset dataset = EvaluationDataset.load();

    @Test
    void shouldContainAtLeastTenUniquelyIdentifiedCasesMarkedAsSynthetic() {
        assertThat(dataset.description()).containsIgnoringCase("synthetic");
        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(MINIMUM_CASES);
        assertThat(dataset.cases()).extracting(EvaluationCase::id).doesNotHaveDuplicates().allMatch(id -> !id.isBlank());
    }

    @Test
    void shouldCoverEveryRequiredCategory() {
        Set<EvaluationCategory> covered = dataset.cases().stream()
                .map(EvaluationCase::category)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EvaluationCategory.class)));

        assertThat(covered).containsExactlyInAnyOrder(EvaluationCategory.values());
    }

    @Test
    void shouldHaveQuestionsAndStructurallyValidExpectations() {
        assertThat(dataset.cases()).allSatisfy(evaluationCase -> {
            assertThat(evaluationCase.question()).as(evaluationCase.id()).isNotBlank();
            assertThat(evaluationCase.expectations()).as(evaluationCase.id()).isNotNull();
            assertThat(evaluationCase.expectations().expectedLlmCallCount()).as(evaluationCase.id()).isPositive();
            assertThat(evaluationCase.expectations().requiredFacts()).as(evaluationCase.id()).allMatch(fact -> !fact.isBlank());
        });
    }

    @Test
    void shouldReferToExistingAgentTools() {
        Set<String> agentTools = Stream.of(ContractTools.class, InstallmentTools.class, EligibilityTools.class)
                .flatMap(toolsClass -> ToolSpecifications.toolSpecificationsFrom(toolsClass).stream())
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertThat(dataset.cases())
                .flatMap(evaluationCase -> evaluationCase.expectations().expectedTools())
                .isSubsetOf(agentTools);
    }

    @Test
    void shouldReferToBundledKnowledgeDocuments() {
        Set<String> bundledDocumentIds = new BundledKnowledgeDocuments().load().stream()
                .map(KnowledgeDocument::documentId)
                .collect(Collectors.toSet());

        assertThat(dataset.cases())
                .flatMap(evaluationCase -> evaluationCase.expectations().retrievalDocumentIds())
                .isSubsetOf(bundledDocumentIds);
    }

    @Test
    void shouldUseCompilableConceptPatterns() {
        List<String> patterns = dataset.cases().stream()
                .flatMap(evaluationCase -> Stream.concat(
                        evaluationCase.expectations().requiredConcepts().stream(),
                        evaluationCase.expectations().forbiddenConcepts().stream()))
                .flatMap(concept -> concept.anyOf().stream())
                .toList();

        assertThat(patterns).isNotEmpty().allSatisfy(pattern ->
                assertThatCode(() -> AnswerExpectations.compile(pattern)).doesNotThrowAnyException());
    }

    @Test
    void shouldDeclarePiiAndPlaceholdersForPiiScenario() {
        assertThat(dataset.cases())
                .filteredOn(evaluationCase -> evaluationCase.category() == EvaluationCategory.PII_MASKING)
                .singleElement()
                .satisfies(piiCase -> {
                    assertThat(piiCase.expectations().rawPiiValues()).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(piiCase.expectations().expectedPlaceholders())
                            .contains("[PESEL_1]", "[CONTRACT_NUMBER_1]");
                });
    }

    @Test
    void shouldMatchAnswersTolerantlyButNotLooselyAgainstTheirExpectations() {
        EvaluationCase installment = caseOf(EvaluationCategory.INSTALLMENT_CALCULATION_TOOL);
        EvaluationCase unsupported = caseOf(EvaluationCategory.UNSUPPORTED_FUTURE_INFORMATION);

        assertThat(AnswerExpectations.violations(
                "The monthly installment is **2,027.64** and the total repayment is 121,658.40.",
                installment.expectations())).isEmpty();
        assertThat(AnswerExpectations.violations(
                "The monthly installment is 2028.00.", installment.expectations())).isNotEmpty();
        assertThat(AnswerExpectations.violations(
                "The available knowledge base does not contain enough information to answer.",
                unsupported.expectations())).isEmpty();
        assertThat(AnswerExpectations.violations(
                "Next month the bank will offer 7.5% to this customer.", unsupported.expectations()))
                .anyMatch(violation -> violation.contains("forbidden"));
    }

    @Test
    void shouldKeepUnderscoresOfDecisionAndReasonCodesWhenRemovingMarkdown() {
        EvaluationCase eligible = caseOf(EvaluationCategory.ELIGIBLE_CUSTOMER);
        EvaluationCase ineligible = caseOf(EvaluationCategory.INELIGIBLE_CUSTOMER);
        String rejection = "The decision is **NOT_ELIGIBLE** (reason code: `LOAN_AMOUNT_TOO_HIGH`). "
                + "The requested amount exceeds the maximum of 72,000.";

        assertThat(AnswerExpectations.violations(rejection, ineligible.expectations())).isEmpty();
        assertThat(AnswerExpectations.violations(rejection, eligible.expectations()))
                .anyMatch(violation -> violation.contains("forbidden concept 'negative decision'"));
    }

    @Test
    void shouldAskTheFaqCaseForEveryOperationalConditionItExpects() {
        String question = caseOf(EvaluationCategory.FAQ_RETRIEVAL).question();

        assertThat(question)
                .as("frequency").containsPattern("(?i)how often|how many times|frequency")
                .as("available dates").containsPattern("(?i)which (dates|days)|what (dates|days)|dates are available")
                .as("fee or cost").containsPattern("(?i)\\b(fee|charge|cost)s?\\b")
                .as("advance notice").containsPattern("(?i)in advance|notice|how (far|early|long) before");
    }

    @Test
    void shouldAskTheCombinedCaseForBothContractStateAndAProductRule() {
        EvaluationCase combined = caseOf(EvaluationCategory.COMBINED_RAG_AND_TOOL);

        assertThat(new PiiMasker().mask(combined.question()).maskedText().detectedCategories())
                .as("refers to a specific contract").contains(PiiCategory.CONTRACT_NUMBER);
        assertThat(combined.question()).as("asks for the contract's state").containsPattern("(?i)\\b(status|state)\\b");
        assertThat(combined.expectations().expectedTools()).as("needs contract facts").contains("getContractStatus");
        assertThat(combined.expectations().retrievalDocumentIds()).as("needs product documentation").isNotEmpty();
    }

    @Test
    void shouldAllowStatingThatNoPercentageIsKnownButForbidInventedNumericRates() {
        EvaluationCase.Expectations unsupported = caseOf(EvaluationCategory.UNSUPPORTED_FUTURE_INFORMATION).expectations();
        EvaluationCase.Expectations missingKnowledge = caseOf(EvaluationCategory.MISSING_PRODUCT_KNOWLEDGE).expectations();

        List.of(
                "No percentage can be determined from the documentation.",
                "The documentation does not specify an insurance percentage.",
                "I cannot determine the premium percentage."
        ).forEach(allowed -> {
            assertThat(forbiddenViolations(allowed, unsupported)).as(allowed).isEmpty();
            assertThat(forbiddenViolations(allowed, missingKnowledge)).as(allowed).isEmpty();
        });

        List.of("The bank will offer 8%.", "The rate will be 7.5 percent.", "The rate will be 8 per cent.")
                .forEach(invented -> assertThat(forbiddenViolations(invented, unsupported)).as(invented).isNotEmpty());
        List.of("The insurance premium is 3%.", "The insurance premium is 3 percent.", "The premium is 250 PLN.",
                        "A monthly premium of about 40 applies.")
                .forEach(invented -> assertThat(forbiddenViolations(invented, missingKnowledge)).as(invented).isNotEmpty());
    }

    @Test
    void shouldKeepEvaluationQuestionsAndTestValuesOutOfTheSystemPrompt() throws NoSuchMethodException {
        String systemPrompt = String.join(" ", CreditAssistant.class
                .getMethod("chat", String.class, InvocationParameters.class)
                .getAnnotation(SystemMessage.class)
                .value()).replaceAll("\\s+", " ");

        assertThat(systemPrompt)
                .as("contract-tool rule")
                .containsIgnoringCase("when answering about the state or facts of a specific contract")
                .contains("call getContractStatus first")
                .contains("never instruct the advisor to call a tool")
                .doesNotContainIgnoringCase("when applying product rules to a specific contract");
        assertThat(systemPrompt)
                .as("generic RAG completeness rule")
                .contains("include all material conditions, limits, deadlines, fees and available options")
                .contains("do not add facts that are not in the documentation");
        assertThat(systemPrompt)
                .as("no values specific to an evaluation case")
                .doesNotContainIgnoringCase("5th")
                .doesNotContainIgnoringCase("15th")
                .doesNotContainIgnoringCase("25th")
                .doesNotContainIgnoringCase("10 days")
                .doesNotContainIgnoringCase("free of charge");
        assertThat(dataset.cases()).allSatisfy(evaluationCase -> {
            assertThat(systemPrompt).doesNotContainIgnoringCase(evaluationCase.question());
            evaluationCase.expectations().rawPiiValues()
                    .forEach(rawValue -> assertThat(systemPrompt).doesNotContainIgnoringCase(rawValue));
        });
        assertThat(systemPrompt).doesNotContainIgnoringCase("whenever a message refers to a specific contract");
    }

    private List<String> forbiddenViolations(String answer, EvaluationCase.Expectations expectations) {
        return AnswerExpectations.violations(answer, expectations).stream()
                .filter(violation -> violation.contains("forbidden"))
                .toList();
    }

    private EvaluationCase caseOf(EvaluationCategory category) {
        return dataset.cases().stream()
                .filter(evaluationCase -> evaluationCase.category() == category)
                .findFirst()
                .orElseThrow();
    }
}
