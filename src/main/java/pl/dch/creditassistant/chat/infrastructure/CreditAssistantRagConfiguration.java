package pl.dch.creditassistant.chat.infrastructure;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.dch.creditassistant.knowledge.application.KnowledgeRetriever;

/**
 * RAG wiring of the {@code CreditAssistant} AI service: the single {@link RetrievalAugmentor} bean is picked up
 * automatically by {@code @AiService}. It is equivalent to configuring the content retriever directly, plus the
 * advisor interaction correlation.
 */
@Configuration(proxyBeanMethods = false)
class CreditAssistantRagConfiguration {

    @Bean
    RetrievalAugmentor creditAssistantRetrievalAugmentor(KnowledgeRetriever knowledgeRetriever) {
        RetrievalAugmentor productKnowledgeAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(new ProductKnowledgeContentRetriever(knowledgeRetriever))
                .build();

        return new InteractionCorrelatingRetrievalAugmentor(productKnowledgeAugmentor);
    }
}
