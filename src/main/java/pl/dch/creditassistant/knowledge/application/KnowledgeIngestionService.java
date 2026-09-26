package pl.dch.creditassistant.knowledge.application;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocument;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * AC-005 ingestion: normalization -> chunking -> embedding -> vector persistence.
 * Re-ingesting a document replaces its previously stored chunks, so repeated runs do not create duplicates.
 */
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final TextNormalizer textNormalizer = new TextNormalizer();

    public KnowledgeIngestionService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            DocumentSplitter documentSplitter
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentSplitter = documentSplitter;
    }

    public void ingest(List<KnowledgeDocument> documents) {
        for (KnowledgeDocument document : documents) {
            ingest(document);
        }
    }

    public void ingest(KnowledgeDocument knowledgeDocument) {
        List<TextSegment> segments = split(knowledgeDocument);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        removeStoredChunks(knowledgeDocument.documentId());
        embeddingStore.addAll(embeddings, segments);

        log.info("Ingested knowledge document {} version {} as {} chunks",
                knowledgeDocument.documentId(), knowledgeDocument.version(), segments.size());
    }

    List<TextSegment> split(KnowledgeDocument knowledgeDocument) {
        String normalizedContent = textNormalizer.normalize(knowledgeDocument.content());
        Document document = Document.from(normalizedContent, toMetadata(knowledgeDocument));

        return documentSplitter.split(document);
    }

    private void removeStoredChunks(String documentId) {
        embeddingStore.removeAll(metadataKey(KnowledgeMetadataKeys.DOCUMENT_ID).isEqualTo(documentId));
    }

    private Metadata toMetadata(KnowledgeDocument knowledgeDocument) {
        return new Metadata()
                .put(KnowledgeMetadataKeys.DOCUMENT_ID, knowledgeDocument.documentId())
                .put(KnowledgeMetadataKeys.DOCUMENT_TITLE, knowledgeDocument.title())
                .put(KnowledgeMetadataKeys.DOCUMENT_TYPE, knowledgeDocument.documentType().name())
                .put(KnowledgeMetadataKeys.DOCUMENT_VERSION, knowledgeDocument.version());
    }
}
