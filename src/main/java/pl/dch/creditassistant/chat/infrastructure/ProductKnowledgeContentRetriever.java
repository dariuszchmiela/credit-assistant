package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.springframework.stereotype.Component;
import pl.dch.creditassistant.knowledge.application.KnowledgeRetriever;
import pl.dch.creditassistant.knowledge.domain.KnowledgeChunk;

import java.util.List;

/**
 * FR-002: augments every chat request with relevant product documentation retrieved by the knowledge module.
 * Picked up automatically by the {@code @AiService} as its single {@link ContentRetriever}.
 */
@Component
public class ProductKnowledgeContentRetriever implements ContentRetriever {

    private final KnowledgeRetriever knowledgeRetriever;

    public ProductKnowledgeContentRetriever(KnowledgeRetriever knowledgeRetriever) {
        this.knowledgeRetriever = knowledgeRetriever;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return knowledgeRetriever.retrieve(query.text()).stream()
                .map(this::toContent)
                .toList();
    }

    private Content toContent(KnowledgeChunk chunk) {
        return Content.from("[Product documentation: " + chunk.documentTitle() + "]\n" + chunk.content());
    }
}
