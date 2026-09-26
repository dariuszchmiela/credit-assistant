package pl.dch.creditassistant.knowledge.application;

final class KnowledgeMetadataKeys {

    static final String DOCUMENT_ID = "document_id";
    static final String DOCUMENT_TITLE = "document_title";
    static final String DOCUMENT_TYPE = "document_type";
    static final String DOCUMENT_VERSION = "document_version";

    /**
     * Segment position within its document, added by LangChain4j document splitters.
     */
    static final String SEQUENCE_NUMBER = "index";

    private KnowledgeMetadataKeys() {
    }
}
