package pl.dch.creditassistant.knowledge.application;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import pl.dch.creditassistant.knowledge.domain.KnowledgeChunk;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocumentType;

import java.util.List;

/**
 * Runtime semantic retrieval (AC-005): query embedding -> similarity search -> relevant chunks.
 * Returns knowledge-module types only, so vector-store objects do not cross the module boundary.
 */
public class KnowledgeRetriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int maxResults;
    private final double minRelevanceScore;

    public KnowledgeRetriever(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            int maxResults,
            double minRelevanceScore
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.maxResults = maxResults;
        this.minRelevanceScore = minRelevanceScore;
    }

    public List<KnowledgeChunk> retrieve(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minRelevanceScore)
                .build();

        return embeddingStore.search(searchRequest).matches().stream()
                .map(this::toKnowledgeChunk)
                .toList();
    }

    private KnowledgeChunk toKnowledgeChunk(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();

        return new KnowledgeChunk(
                metadata.getString(KnowledgeMetadataKeys.DOCUMENT_ID),
                metadata.getString(KnowledgeMetadataKeys.DOCUMENT_TITLE),
                KnowledgeDocumentType.valueOf(metadata.getString(KnowledgeMetadataKeys.DOCUMENT_TYPE)),
                metadata.getString(KnowledgeMetadataKeys.DOCUMENT_VERSION),
                Integer.parseInt(metadata.getString(KnowledgeMetadataKeys.SEQUENCE_NUMBER)),
                segment.text(),
                match.score()
        );
    }
}
