package pl.dch.creditassistant.knowledge.application;

import pl.dch.creditassistant.knowledge.domain.KnowledgeChunk;

import java.util.List;

/**
 * Runtime semantic retrieval of product knowledge (FR-002, SPEC 11.5).
 * The number of results and the minimum relevance score are configuration of the implementation.
 */
public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(String query);
}
