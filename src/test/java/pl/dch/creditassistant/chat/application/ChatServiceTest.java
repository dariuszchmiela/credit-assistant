package pl.dch.creditassistant.chat.application;

import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.privacy.application.PiiMasker;
import pl.dch.creditassistant.privacy.domain.PiiCategory;
import pl.dch.creditassistant.privacy.domain.ProtectedValues;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-007: only masked text crosses the LLM boundary; originals stay in the internal invocation parameters.
 * Uses a stub {@link CreditAssistant}, no LLM.
 */
class ChatServiceTest {

    private static final String RAW_CONTRACT_NUMBER = "CTR-1001";
    private static final String RAW_PESEL = "44051401458";

    private final RecordingCreditAssistant creditAssistant = new RecordingCreditAssistant();
    private final ChatService chatService = new ChatService(new PiiMasker(), creditAssistant);

    @Test
    void shouldSendOnlyMaskedMessageToTheAssistant() {
        chatService.chat("Check contract " + RAW_CONTRACT_NUMBER + " for customer PESEL " + RAW_PESEL + ".");

        assertThat(creditAssistant.maskedMessage)
                .isEqualTo("Check contract [CONTRACT_NUMBER_1] for customer PESEL [PESEL_1].")
                .doesNotContain(RAW_CONTRACT_NUMBER)
                .doesNotContain(RAW_PESEL);
    }

    @Test
    void shouldPassContractResolutionContextInInvocationParameters() {
        chatService.chat("Status of " + RAW_CONTRACT_NUMBER + "?");

        ProtectedValues protectedValues = ChatInvocationParameters.protectedValues(creditAssistant.invocationParameters);
        assertThat(protectedValues.originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .as("placeholder resolves internally")
                .isPresent()
                .hasValueSatisfying(original -> assertThat(original.equals(RAW_CONTRACT_NUMBER)).isTrue());
    }

    @Test
    void shouldPassContractMappingOnlyAndNoRawPeselInInvocationParameters() {
        chatService.chat("Check contract ctr-1001 for customer PESEL " + RAW_PESEL + ".");

        ProtectedValues protectedValues = ChatInvocationParameters.protectedValues(creditAssistant.invocationParameters);
        assertThat(protectedValues.originalOf(PiiCategory.PESEL, "[PESEL_1]")).isEmpty();
        assertThat(protectedValues.originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .as("canonical contract number resolves internally")
                .hasValueSatisfying(original -> assertThat(original.equals(RAW_CONTRACT_NUMBER)).isTrue());
        assertThat(creditAssistant.invocationParameters.asMap())
                .usingRecursiveAssertion()
                .allFieldsSatisfy(field -> !String.valueOf(field).contains(RAW_PESEL));
    }

    @Test
    void shouldReturnTheAssistantAnswer() {
        String answer = chatService.chat("Can the customer repay early?");

        assertThat(answer).isEqualTo(RecordingCreditAssistant.ANSWER);
        assertThat(creditAssistant.maskedMessage).isEqualTo("Can the customer repay early?");
    }

    private static final class RecordingCreditAssistant implements CreditAssistant {

        private static final String ANSWER = "stub answer";

        private String maskedMessage;
        private InvocationParameters invocationParameters;

        @Override
        public String chat(String maskedMessage, InvocationParameters invocationParameters) {
            this.maskedMessage = maskedMessage;
            this.invocationParameters = invocationParameters;
            return ANSWER;
        }
    }
}
