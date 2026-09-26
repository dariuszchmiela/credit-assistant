package pl.dch.creditassistant.knowledge.infrastructure;

import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.dch.creditassistant.TestcontainersConfiguration;
import pl.dch.creditassistant.knowledge.application.KnowledgeIngestionService;
import pl.dch.creditassistant.knowledge.application.KnowledgeRetriever;
import pl.dch.creditassistant.knowledge.domain.KnowledgeChunk;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-002, FR-011, AC-005: ingestion and semantic retrieval against real PostgreSQL + pgvector
 * using the real local embedding model. Does not require Ollama.
 * Bundled documents are ingested on context startup by {@link KnowledgeIngestionRunner}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PgVectorKnowledgeIntegrationTest {

    private static final String EARLY_REPAYMENT_DOCUMENT_ID = "early-repayment";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KnowledgeProperties knowledgeProperties;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private BundledKnowledgeDocuments bundledKnowledgeDocuments;

    @Autowired
    private KnowledgeIngestionService knowledgeIngestionService;

    @Autowired
    private KnowledgeRetriever knowledgeRetriever;

    @Test
    void shouldProvidePostgresWithUsablePgvectorExtension() {
        String extensionVersion = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        Double distance = jdbcTemplate.queryForObject(
                "SELECT '[1,2,3]'::vector <-> '[1,2,5]'::vector", Double.class);

        assertThat(extensionVersion).isNotBlank();
        assertThat(distance).isEqualTo(2.0);
    }

    @Test
    void shouldPersistBundledDocumentChunksWithEmbeddingsOfModelDimension() {
        List<Map<String, Object>> chunksPerDocument = jdbcTemplate.queryForList(
                "SELECT metadata->>'document_id' AS document_id, count(*) AS chunks FROM "
                        + knowledgeProperties.embeddingTable() + " GROUP BY 1");
        List<Integer> embeddingDimensions = jdbcTemplate.queryForList(
                "SELECT DISTINCT vector_dims(embedding) FROM " + knowledgeProperties.embeddingTable(), Integer.class);

        assertThat(chunksPerDocument)
                .extracting(row -> row.get("document_id"))
                .containsExactlyInAnyOrder(EARLY_REPAYMENT_DOCUMENT_ID, "consumer-loan-regulations", "consumer-loan-faq");
        assertThat(chunksPerDocument)
                .allSatisfy(row -> assertThat((Long) row.get("chunks")).isPositive());
        assertThat(embeddingDimensions).containsExactly(embeddingModel.dimension());
    }

    @Test
    void shouldNotDuplicateChunksWhenBundledDocumentsAreIngestedAgain() {
        int chunksBefore = countStoredChunks();

        knowledgeIngestionService.ingest(bundledKnowledgeDocuments.load());

        assertThat(countStoredChunks()).isEqualTo(chunksBefore);
    }

    @Test
    void shouldRetrieveEarlyRepaymentDocumentationForSemanticallyEquivalentQuestion() {
        List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve("Can I repay my loan before the end of the agreement?");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.getFirst().documentId()).isEqualTo(EARLY_REPAYMENT_DOCUMENT_ID);
        assertThat(chunks.getFirst().documentTitle()).isEqualTo("Early Repayment of the Sample Consumer Loan");
        assertThat(chunks.getFirst().content()).containsIgnoringCase("early repayment");
    }

    @Test
    void shouldRetrieveEarlyRepaymentFeeRuleForFeeQuestion() {
        List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve(
                "Is the customer charged anything extra for paying off the loan ahead of schedule?");

        assertThat(chunks)
                .anySatisfy(chunk -> {
                    assertThat(chunk.documentId()).isEqualTo(EARLY_REPAYMENT_DOCUMENT_ID);
                    assertThat(chunk.content()).contains("no additional fee");
                });
    }

    @Test
    void shouldRetrieveNothingForQuestionUnrelatedToProductDocumentation() {
        List<KnowledgeChunk> chunks = knowledgeRetriever.retrieve("What is the status of contract CTR-1003?");

        assertThat(chunks).isEmpty();
    }

    private int countStoredChunks() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + knowledgeProperties.embeddingTable(), Integer.class);
    }
}
