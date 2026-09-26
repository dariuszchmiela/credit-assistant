package pl.dch.creditassistant.knowledge.application;

import pl.dch.creditassistant.knowledge.domain.KnowledgeDocument;

import java.util.List;

/**
 * Ingestion of product knowledge documents into the vector store (AC-005).
 * Re-ingesting a document replaces its previously stored chunks.
 */
public interface KnowledgeIngestionService {

    void ingest(KnowledgeDocument document);

    default void ingest(List<KnowledgeDocument> documents) {
        for (KnowledgeDocument document : documents) {
            ingest(document);
        }
    }
}
