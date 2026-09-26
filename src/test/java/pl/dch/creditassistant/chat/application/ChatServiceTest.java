package pl.dch.creditassistant.chat.application;

import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.observability.application.AdvisorInteractionRecorder;
import pl.dch.creditassistant.observability.domain.AdvisorInteraction;
import pl.dch.creditassistant.observability.domain.AdvisorInteractionStatus;
import pl.dch.creditassistant.privacy.application.PiiMasker;
import pl.dch.creditassistant.privacy.domain.PiiCategory;
import pl.dch.creditassistant.privacy.domain.PiiMaskingResult;
import pl.dch.creditassistant.privacy.domain.ProtectedValues;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-007: only masked text crosses the LLM boundary; originals stay in the internal invocation parameters.
 * FR-008: one advisor interaction per request, correlated through the invocation parameters.
 * Uses a stub {@link CreditAssistant} and an in-memory advisor interaction repository; no LLM, no database.
 */
class ChatServiceTest {

    private static final String RAW_CONTRACT_NUMBER = "CTR-1001";
    private static final String RAW_PESEL = "44051401458";

    private final RecordingCreditAssistant creditAssistant = new RecordingCreditAssistant();
    private final List<AdvisorInteraction> savedInteractions = new ArrayList<>();
    private final ChatService chatService = chatService(new PiiMasker());

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

    @Test
    void shouldStartAndCompleteOneSuccessfulAdvisorInteractionCorrelatedThroughInvocationParameters() {
        creditAssistant.answer = "Contract " + RAW_CONTRACT_NUMBER + " is ACTIVE.";

        String answer = chatService.chat("Status of " + RAW_CONTRACT_NUMBER + " for PESEL " + RAW_PESEL + "?");

        UUID interactionId = ChatInvocationParameters.interactionId(creditAssistant.invocationParameters);
        assertThat(interactionId).isNotNull();
        assertThat(answer).as("answer returned unchanged").isEqualTo(creditAssistant.answer);
        assertThat(savedInteractions).hasSize(2).allSatisfy(interaction -> {
            assertThat(interaction.interactionId()).isEqualTo(interactionId);
            assertThat(interaction.maskedAdvisorMessage()).isEqualTo("Status of [CONTRACT_NUMBER_1] for PESEL [PESEL_1]?");
        });
        assertThat(savedInteractions.getFirst().status()).as("saved when started").isNull();
        assertThat(savedInteractions.getLast()).satisfies(completed -> {
            assertThat(completed.status()).isEqualTo(AdvisorInteractionStatus.SUCCESS);
            assertThat(completed.maskedFinalResponse()).isEqualTo("Contract [CONTRACT_NUMBER_1] is ACTIVE.");
            assertThat(completed.completedAt()).isAfterOrEqualTo(completed.startedAt());
            assertThat(completed.durationMillis()).isNotNegative();
        });
    }

    @Test
    void shouldUseANewInteractionIdForEveryAdvisorRequest() {
        chatService.chat("first");
        UUID firstInteractionId = ChatInvocationParameters.interactionId(creditAssistant.invocationParameters);
        chatService.chat("second");
        UUID secondInteractionId = ChatInvocationParameters.interactionId(creditAssistant.invocationParameters);

        assertThat(firstInteractionId).isNotEqualTo(secondInteractionId);
    }

    @Test
    void shouldMarkInteractionFailedAndRethrowWhenTheAssistantFails() {
        IllegalStateException modelFailure = new IllegalStateException("provider echoed " + RAW_PESEL);
        creditAssistant.failure = modelFailure;

        assertThatThrownBy(() -> chatService.chat("Status of " + RAW_CONTRACT_NUMBER + "?")).isSameAs(modelFailure);

        assertThat(savedInteractions.getLast()).satisfies(failed -> {
            assertThat(failed.status()).isEqualTo(AdvisorInteractionStatus.FAILED);
            assertThat(failed.maskedFinalResponse()).isNull();
            assertThat(failed.maskedAdvisorMessage()).isEqualTo("Status of [CONTRACT_NUMBER_1]?");
            assertThat(failed.durationMillis()).isNotNegative();
            assertThat(failed.toString()).doesNotContain(RAW_PESEL);
        });
    }

    @Test
    void shouldRejectWithoutCallingTheAssistantOrKeepingTheMessageWhenMaskingFails() {
        PiiMasker failingMasker = new PiiMasker() {
            @Override
            public PiiMaskingResult mask(String text) {
                throw new IllegalStateException("masking failed on " + text);
            }
        };
        ChatService serviceWithFailingMasker = chatService(failingMasker);

        assertThatThrownBy(() -> serviceWithFailingMasker.chat("PESEL " + RAW_PESEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Advisor message rejected: PII masking failed")
                .hasNoCause();

        assertThat(creditAssistant.maskedMessage).as("assistant not called").isNull();
        assertThat(savedInteractions).singleElement().satisfies(rejected -> {
            assertThat(rejected.status()).isEqualTo(AdvisorInteractionStatus.REJECTED_PRIVACY);
            assertThat(rejected.maskedAdvisorMessage()).isNull();
            assertThat(rejected.maskedFinalResponse()).isNull();
            assertThat(rejected.durationMillis()).isNotNegative();
            assertThat(rejected.toString()).doesNotContain(RAW_PESEL);
        });
    }

    private ChatService chatService(PiiMasker piiMasker) {
        return new ChatService(piiMasker, creditAssistant, new AdvisorInteractionRecorder(savedInteractions::add));
    }

    private static final class RecordingCreditAssistant implements CreditAssistant {

        private static final String ANSWER = "stub answer";

        private String maskedMessage;
        private InvocationParameters invocationParameters;
        private String answer = ANSWER;
        private RuntimeException failure;

        @Override
        public String chat(String maskedMessage, InvocationParameters invocationParameters) {
            this.maskedMessage = maskedMessage;
            this.invocationParameters = invocationParameters;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }
}
