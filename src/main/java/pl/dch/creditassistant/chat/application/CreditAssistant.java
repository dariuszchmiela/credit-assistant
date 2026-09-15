package pl.dch.creditassistant.chat.application;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CreditAssistant {

    @SystemMessage("""
            You are an AI assistant supporting a credit advisor.
            Answer clearly and concisely.
            Do not invent credit contract data, calculations or eligibility decisions.
            """)
    String chat(String message);
}