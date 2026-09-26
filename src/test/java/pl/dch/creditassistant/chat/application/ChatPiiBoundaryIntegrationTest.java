package pl.dch.creditassistant.chat.application;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import pl.dch.creditassistant.TestcontainersConfiguration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-007 end to end through the real LangChain4j AI service, RAG augmentation and tool execution.
 * The external LLM is replaced by a scripted {@link ChatModel} that records every request it would send out:
 * the first call asks for {@code getContractStatus}, the second one answers. The contract number is typed in
 * lower case, so the test also proves canonical resolution to the existing CTR-1001 contract.
 */
@SpringBootTest(properties = "spring.autoconfigure.exclude=dev.langchain4j.ollama.spring.OllamaAutoConfiguration")
@Import({TestcontainersConfiguration.class, ChatPiiBoundaryIntegrationTest.ScriptedChatModelConfiguration.class})
class ChatPiiBoundaryIntegrationTest {

    private static final String RAW_CONTRACT_NUMBER = "ctr-1001";
    private static final String RAW_PESEL = "44051401458";

    @Autowired
    private ChatService chatService;

    @Autowired
    private ScriptedChatModel chatModel;

    @Test
    void shouldKeepRawPiiOutOfEveryLlmRequestIncludingToolResults() {
        String answer = chatService.chat(
                "Can the customer with PESEL " + RAW_PESEL + " repay contract " + RAW_CONTRACT_NUMBER + " early?");

        List<ChatRequest> requests = chatModel.requests;
        assertThat(requests).as("tool call round trip").hasSize(2);
        assertThat(requests).allSatisfy(request -> assertThat(request.messages().toString())
                .doesNotContainIgnoringCase(RAW_CONTRACT_NUMBER)
                .doesNotContain(RAW_PESEL));

        assertThat(requests.getFirst().messages().getLast().toString())
                .as("masked user message augmented with retrieved product documentation")
                .contains("[CONTRACT_NUMBER_1]", "[PESEL_1]", "Early Repayment");

        ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) requests.getLast().messages().getLast();
        assertThat(toolResult.text()).isEqualTo("contractReference=[CONTRACT_NUMBER_1], status=ACTIVE, "
                + "outstandingPrincipal=85000.00, nextPaymentDate=2026-10-15");
        assertThat(answer).isEqualTo(ScriptedChatModel.ANSWER);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ScriptedChatModelConfiguration {

        @Bean
        ScriptedChatModel scriptedChatModel() {
            return new ScriptedChatModel();
        }
    }

    static class ScriptedChatModel implements ChatModel {

        static final String ANSWER = "Contract [CONTRACT_NUMBER_1] is ACTIVE.";

        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            ChatMessage lastMessage = chatRequest.messages().getLast();

            AiMessage reply = lastMessage instanceof ToolExecutionResultMessage
                    ? AiMessage.from(ANSWER)
                    : AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-1")
                            .name("getContractStatus")
                            .arguments("{\"contractReference\":\"[CONTRACT_NUMBER_1]\"}")
                            .build());

            return ChatResponse.builder().aiMessage(reply).build();
        }
    }
}
