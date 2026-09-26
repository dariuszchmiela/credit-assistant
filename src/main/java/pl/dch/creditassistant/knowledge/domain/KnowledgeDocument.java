package pl.dch.creditassistant.knowledge.domain;

public record KnowledgeDocument(
        String documentId,
        String title,
        KnowledgeDocumentType documentType,
        String version,
        String content
) {
}
