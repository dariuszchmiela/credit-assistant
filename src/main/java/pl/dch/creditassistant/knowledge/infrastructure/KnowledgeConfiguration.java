package pl.dch.creditassistant.knowledge.infrastructure;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.dch.creditassistant.knowledge.application.KnowledgeIngestionService;
import pl.dch.creditassistant.knowledge.application.KnowledgeRetriever;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeProperties.class)
class KnowledgeConfiguration {

    /**
     * Local in-process embedding model; the chat model is never used for embeddings.
     */
    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Bean
    EmbeddingStore<TextSegment> productKnowledgeEmbeddingStore(
            DataSource dataSource,
            EmbeddingModel embeddingModel,
            KnowledgeProperties properties
    ) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(properties.embeddingTable())
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .build();
    }

    @Bean
    KnowledgeIngestionService knowledgeIngestionService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> productKnowledgeEmbeddingStore,
            KnowledgeProperties properties
    ) {
        KnowledgeProperties.Ingestion ingestion = properties.ingestion();

        return new LangChain4jKnowledgeIngestionService(
                embeddingModel,
                productKnowledgeEmbeddingStore,
                DocumentSplitters.recursive(ingestion.chunkSize(), ingestion.chunkOverlap())
        );
    }

    @Bean
    KnowledgeRetriever knowledgeRetriever(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> productKnowledgeEmbeddingStore,
            KnowledgeProperties properties
    ) {
        KnowledgeProperties.Retrieval retrieval = properties.retrieval();

        return new LangChain4jKnowledgeRetriever(
                embeddingModel,
                productKnowledgeEmbeddingStore,
                retrieval.maxResults(),
                retrieval.minRelevanceScore()
        );
    }
}
