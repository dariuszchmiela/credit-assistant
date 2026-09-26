package pl.dch.creditassistant.knowledge.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.knowledge.application.KnowledgeIngestionService;

/**
 * Demo bootstrap: (re)ingests the bundled product documents on startup when {@code knowledge.ingestion.enabled=true}.
 * Only chunks of the bundled documents are replaced; nothing else in the database is touched.
 */
@Component
@ConditionalOnBooleanProperty("knowledge.ingestion.enabled")
class KnowledgeIngestionRunner implements ApplicationRunner {

    private final BundledKnowledgeDocuments bundledKnowledgeDocuments;
    private final KnowledgeIngestionService knowledgeIngestionService;

    KnowledgeIngestionRunner(
            BundledKnowledgeDocuments bundledKnowledgeDocuments,
            KnowledgeIngestionService knowledgeIngestionService
    ) {
        this.bundledKnowledgeDocuments = bundledKnowledgeDocuments;
        this.knowledgeIngestionService = knowledgeIngestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        knowledgeIngestionService.ingest(bundledKnowledgeDocuments.load());
    }
}
