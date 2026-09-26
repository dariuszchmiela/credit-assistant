package pl.dch.creditassistant.knowledge.domain;

public record KnowledgeChunk(
        String documentId,
        String documentTitle,
        KnowledgeDocumentType documentType,
        String documentVersion,
        int sequenceNumber,
        String content,
        double relevanceScore
) {
}
