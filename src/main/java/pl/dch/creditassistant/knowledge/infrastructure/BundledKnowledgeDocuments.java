package pl.dch.creditassistant.knowledge.infrastructure;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocument;
import pl.dch.creditassistant.knowledge.domain.KnowledgeDocumentType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Catalogue of the mock product documents bundled under {@code src/main/resources/knowledge}.
 */
@Component
public class BundledKnowledgeDocuments {

    private static final String DOCUMENT_VERSION = "1";

    private static final List<BundledDocument> DOCUMENTS = List.of(
            new BundledDocument(
                    "early-repayment",
                    "Early Repayment of the Sample Consumer Loan",
                    KnowledgeDocumentType.REGULATION,
                    "knowledge/early-repayment.md"
            ),
            new BundledDocument(
                    "consumer-loan-regulations",
                    "Sample Consumer Loan Regulations",
                    KnowledgeDocumentType.REGULATION,
                    "knowledge/consumer-loan-regulations.md"
            ),
            new BundledDocument(
                    "consumer-loan-faq",
                    "Sample Consumer Loan FAQ",
                    KnowledgeDocumentType.FAQ,
                    "knowledge/consumer-loan-faq.md"
            )
    );

    public List<KnowledgeDocument> load() {
        return DOCUMENTS.stream()
                .map(this::load)
                .toList();
    }

    private KnowledgeDocument load(BundledDocument document) {
        return new KnowledgeDocument(
                document.documentId(),
                document.title(),
                document.documentType(),
                DOCUMENT_VERSION,
                readClassPathResource(document.location())
        );
    }

    private String readClassPathResource(String location) {
        try {
            return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read bundled knowledge document " + location, exception);
        }
    }

    private record BundledDocument(
            String documentId,
            String title,
            KnowledgeDocumentType documentType,
            String location
    ) {
    }
}
