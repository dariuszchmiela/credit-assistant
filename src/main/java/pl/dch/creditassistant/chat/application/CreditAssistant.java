package pl.dch.creditassistant.chat.application;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface CreditAssistant {

    @SystemMessage("""
            You are an AI assistant supporting a credit advisor.
            Answer clearly and concisely.
            Do not invent credit contract data, calculations or eligibility decisions.
            Do not assume or invent a currency. If no currency is provided, return monetary values without a currency symbol.
            """)
    String chat(String message);
}