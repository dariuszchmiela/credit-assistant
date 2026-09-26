package pl.dch.creditassistant.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * SPEC 19, AC-002, AC-007, AC-009: executable module boundaries of the production code.
 */
@AnalyzeClasses(packages = "pl.dch.creditassistant", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] AI_FRAMEWORK_PACKAGES = {
            "dev.langchain4j..",
            "io.modelcontextprotocol..",
            "org.springframework.ai.."
    };

    private static final String[] PERSISTENCE_FRAMEWORK_PACKAGES = {
            "com.pgvector..",
            "org.postgresql..",
            "org.springframework.jdbc.."
    };

    @ArchTest
    static final ArchRule creditDoesNotDependOnAiFrameworks = noClasses()
            .that().resideInAPackage("pl.dch.creditassistant.credit..")
            .should().dependOnClassesThat().resideInAnyPackage(AI_FRAMEWORK_PACKAGES)
            .because("the whole credit module, adapters included, must stay independent of AI and MCP frameworks (AC-002)");

    @ArchTest
    static final ArchRule creditBusinessLayersDoNotDependOnPersistenceFrameworks = noClasses()
            .that().resideInAnyPackage(
                    "pl.dch.creditassistant.credit..domain..",
                    "pl.dch.creditassistant.credit..application.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORK_PACKAGES)
            .because("persistence adapters belong in credit infrastructure; business layers use ports (AC-004, AC-007)");

    @ArchTest
    static final ArchRule creditDoesNotDependOnOtherModules = noClasses()
            .that().resideInAPackage("pl.dch.creditassistant.credit..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pl.dch.creditassistant.chat..",
                    "pl.dch.creditassistant.knowledge..",
                    "pl.dch.creditassistant.mcp..",
                    "pl.dch.creditassistant.privacy..",
                    "pl.dch.creditassistant.observability.."
            )
            .because("other modules depend on credit, never the other way round (SPEC 16)");

    @ArchTest
    static final ArchRule knowledgeApplicationAndDomainDoNotDependOnAiOrPersistenceFrameworks = noClasses()
            .that().resideInAnyPackage(
                    "pl.dch.creditassistant.knowledge.application..",
                    "pl.dch.creditassistant.knowledge.domain.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(AI_FRAMEWORK_PACKAGES)
            .orShould().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORK_PACKAGES)
            .because("LangChain4j embeddings and pgvector belong to knowledge.infrastructure only (AC-007)");

    @ArchTest
    static final ArchRule chatUsesKnowledgeApplicationApiOnly = noClasses()
            .that().resideInAPackage("pl.dch.creditassistant.chat..")
            .should().dependOnClassesThat().resideInAPackage("pl.dch.creditassistant.knowledge.infrastructure..")
            .orShould().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORK_PACKAGES)
            .because("chat retrieves product knowledge through the knowledge application API, not vector-store details");

    @ArchTest
    static final ArchRule mcpAdaptersDelegateToCreditApplicationServices = noClasses()
            .that().resideInAPackage("pl.dch.creditassistant.mcp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pl.dch.creditassistant.chat..",
                    "pl.dch.creditassistant.credit..infrastructure..",
                    "dev.langchain4j.."
            )
            .because("MCP and LangChain4j tools are independent adapters over the same credit application services (FR-006, SPEC 19)");

    @ArchTest
    static final ArchRule domainIsFreeOfFrameworks = noClasses()
            .that().resideInAPackage("pl.dch.creditassistant..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..")
            .orShould().dependOnClassesThat().resideInAnyPackage(AI_FRAMEWORK_PACKAGES)
            .orShould().dependOnClassesThat().resideInAnyPackage(PERSISTENCE_FRAMEWORK_PACKAGES)
            .because("domain models are plain Java (AC-009)");

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("pl.dch.creditassistant..domain..")
            .should().dependOnClassesThat().resideInAPackage("pl.dch.creditassistant..infrastructure..")
            .because("infrastructure adapts the domain, not the other way round (SPEC 19)");
}
