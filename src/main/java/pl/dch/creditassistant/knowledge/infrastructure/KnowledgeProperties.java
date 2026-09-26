package pl.dch.creditassistant.knowledge.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledge")
public record KnowledgeProperties(
        String embeddingTable,
        Ingestion ingestion,
        Retrieval retrieval
) {

    /**
     * @param enabled      when true, bundled product documents are (re)ingested on startup
     * @param chunkSize    maximum chunk size in characters
     * @param chunkOverlap maximum overlap between neighbouring chunks in characters
     */
    public record Ingestion(
            boolean enabled,
            int chunkSize,
            int chunkOverlap
    ) {
    }

    /**
     * @param maxResults        maximum number of chunks added to the model context
     * @param minRelevanceScore minimum relevance score (0..1) a chunk needs to be used
     */
    public record Retrieval(
            int maxResults,
            double minRelevanceScore
    ) {
    }
}
