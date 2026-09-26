package pl.dch.creditassistant.privacy.application;

import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.privacy.domain.MaskedText;
import pl.dch.creditassistant.privacy.domain.PiiCategory;
import pl.dch.creditassistant.privacy.domain.PiiMaskingResult;
import pl.dch.creditassistant.privacy.domain.ProtectedValues;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-007, SPEC 41: deterministic PII masking without an LLM.
 */
class PiiMaskerTest {

    private static final String PESEL = "44051401458";
    private static final String OTHER_PESEL = "02070803628";

    private final PiiMasker piiMasker = new PiiMasker();

    @Test
    void shouldMaskStandalonePesel() {
        MaskedText masked = piiMasker.mask("Customer PESEL " + PESEL + ".").maskedText();

        assertThat(masked.text()).isEqualTo("Customer PESEL [PESEL_1].");
        assertThat(masked.detectedCategories()).containsExactly(PiiCategory.PESEL);
    }

    @Test
    void shouldNotMaskPartOfLongerDigitSequence() {
        String text = "Reference 123456789012 and account 61109010140000071219812874.";

        MaskedText masked = piiMasker.mask(text).maskedText();

        assertThat(masked.text()).isEqualTo(text);
        assertThat(masked.detectedCategories()).isEmpty();
    }

    @Test
    void shouldReusePlaceholderForRepeatedPeselAndNumberDistinctValues() {
        String text = PESEL + ", " + OTHER_PESEL + ", again " + PESEL;

        assertThat(piiMasker.mask(text).maskedText().text())
                .isEqualTo("[PESEL_1], [PESEL_2], again [PESEL_1]");
    }

    @Test
    void shouldMaskKnownAndUnknownContractNumbers() {
        PiiMaskingResult result = piiMasker.mask("Compare CTR-1001 with CTR-9999.");

        assertThat(result.maskedText().text()).isEqualTo("Compare [CONTRACT_NUMBER_1] with [CONTRACT_NUMBER_2].");
        assertThat(result.maskedText().detectedCategories()).containsExactly(PiiCategory.CONTRACT_NUMBER);
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .contains("CTR-1001");
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_2]"))
                .contains("CTR-9999");
    }

    @Test
    void shouldReusePlaceholderForRepeatedContractNumber() {
        String text = "Check contract CTR-1001 for PESEL " + PESEL + ".\nAgain, contract CTR-1001.";

        assertThat(piiMasker.mask(text).maskedText().text()).isEqualTo(
                "Check contract [CONTRACT_NUMBER_1] for PESEL [PESEL_1].\nAgain, contract [CONTRACT_NUMBER_1].");
    }

    @Test
    void shouldMaskLowerCaseContractNumberAndResolveItToCanonicalForm() {
        PiiMaskingResult result = piiMasker.mask("Status of ctr-1001?");

        assertThat(result.maskedText().text()).isEqualTo("Status of [CONTRACT_NUMBER_1]?");
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .contains("CTR-1001");
    }

    @Test
    void shouldUseOnePlaceholderForContractNumbersDifferingOnlyInCase() {
        PiiMaskingResult result = piiMasker.mask("CTR-1001 and ctr-1001 and Ctr-1001, but CTR-1002");

        assertThat(result.maskedText().text())
                .isEqualTo("[CONTRACT_NUMBER_1] and [CONTRACT_NUMBER_1] and [CONTRACT_NUMBER_1], but [CONTRACT_NUMBER_2]");
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .contains("CTR-1001");
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_2]"))
                .contains("CTR-1002");
    }

    @Test
    void shouldNormalizeLowerCaseSpecFormatContractNumber() {
        PiiMaskingResult result = piiMasker.mask("cr-2026-000123");

        assertThat(result.maskedText().text()).isEqualTo("[CONTRACT_NUMBER_1]");
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .contains("CR-2026-000123");
    }

    @Test
    void shouldMaskOnlySupportedPiiInMixedText() {
        String text = "Customer with PESEL " + PESEL + " and contract CTR-1003 earns 8000, pays 2000 "
                + "and asks for 100000 over 60 months at 8.5%.";

        MaskedText masked = piiMasker.mask(text).maskedText();

        assertThat(masked.text()).isEqualTo("Customer with PESEL [PESEL_1] and contract [CONTRACT_NUMBER_1] "
                + "earns 8000, pays 2000 and asks for 100000 over 60 months at 8.5%.");
        assertThat(masked.detectedCategories())
                .containsExactlyInAnyOrder(PiiCategory.PESEL, PiiCategory.CONTRACT_NUMBER);
    }

    @Test
    void shouldLeaveTextWithoutPiiUnchanged() {
        String text = "Can the customer repay the loan early?";

        PiiMaskingResult result = piiMasker.mask(text);

        assertThat(result.maskedText().text()).isEqualTo(text);
        assertThat(result.maskedText().detectedCategories()).isEmpty();
    }

    @Test
    void shouldMaskPeselIrreversibly() {
        PiiMaskingResult result = piiMasker.mask("PESEL " + PESEL + " and contract CTR-1001");

        assertThat(result.maskedText().detectedCategories())
                .containsExactlyInAnyOrder(PiiCategory.PESEL, PiiCategory.CONTRACT_NUMBER);
        assertThat(result.protectedValues().originalOf(PiiCategory.PESEL, "[PESEL_1]")).isEmpty();
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[PESEL_1]")).isEmpty();
        assertThat(result.protectedValues().originalOf(PiiCategory.CONTRACT_NUMBER, "[CONTRACT_NUMBER_1]"))
                .contains("CTR-1001");
    }

    @Test
    void shouldNotRetainRawPeselInAnyReturnedObject() {
        PiiMaskingResult result = piiMasker.mask("PESEL " + PESEL + ", " + OTHER_PESEL + ", contract CTR-1001");

        assertThat(result)
                .usingRecursiveAssertion()
                .allFieldsSatisfy(field -> !String.valueOf(field).contains(PESEL)
                        && !String.valueOf(field).contains(OTHER_PESEL));
    }

    @Test
    void shouldNeverRevealOriginalValuesInStringRepresentations() {
        PiiMaskingResult result = piiMasker.mask("Contract CTR-1001, PESEL " + PESEL);

        assertThat(result.toString())
                .doesNotContain("CTR-1001")
                .doesNotContain(PESEL);
        assertThat(result.protectedValues().toString())
                .doesNotContain("CTR-1001")
                .doesNotContain(PESEL);
    }
}
