package pl.dch.creditassistant.knowledge.application;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.knowledge.domain.KnowledgeChunk;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocument;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocumentType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-005 ingestion: chunking, metadata propagation and re-ingestion without duplicates.
 * Uses the real local embedding model; pgvector persistence is covered by the Testcontainers integration test.
 */
class KnowledgeIngestionServiceTest {

    private static final int CHUNK_SIZE = 200;
    private static final int CHUNK_OVERLAP = 0;

    private static final KnowledgeDocument DOCUMENT = new KnowledgeDocument(
            "test-document",
            "Test Document",
            KnowledgeDocumentType.FAQ,
            "7",
            """
                    # Test Document

                    ## Payment holidays
                    Payment holidays are available twice per year for customers without overdue installments.

                    ## Card replacement
                    A lost debit card is replaced within five business days after the customer reports it.
                    """
    );

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final KnowledgeIngestionService ingestionService = new KnowledgeIngestionService(
            embeddingModel,
            embeddingStore,
            DocumentSplitters.recursive(CHUNK_SIZE, CHUNK_OVERLAP)
    );
    private final KnowledgeRetriever retriever = new KnowledgeRetriever(embeddingModel, embeddingStore, 10, 0.0);

    @Test
    void shouldSplitDocumentIntoSectionChunksCarryingDocumentMetadata() {
        List<TextSegment> segments = ingestionService.split(DOCUMENT);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).contains("Payment holidays").doesNotContain("Card replacement");
        assertThat(segments.get(1).text()).startsWith("## Card replacement");
        assertThat(segments).allSatisfy(segment -> {
            assertThat(segment.metadata().getString("document_id")).isEqualTo("test-document");
            assertThat(segment.metadata().getString("document_title")).isEqualTo("Test Document");
            assertThat(segment.metadata().getString("document_type")).isEqualTo("FAQ");
            assertThat(segment.metadata().getString("document_version")).isEqualTo("7");
        });
    }

    @Test
    void shouldReplaceChunksOfAlreadyIngestedDocument() {
        ingestionService.ingest(DOCUMENT);
        ingestionService.ingest(DOCUMENT);

        List<KnowledgeChunk> chunks = retriever.retrieve("payment holidays");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(KnowledgeChunk::sequenceNumber).containsExactlyInAnyOrder(0, 1);
        assertThat(chunks.getFirst().documentId()).isEqualTo("test-document");
        assertThat(chunks.getFirst().content()).contains("Payment holidays");
    }
}
