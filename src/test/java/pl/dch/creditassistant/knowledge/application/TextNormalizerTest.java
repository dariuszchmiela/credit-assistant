package pl.dch.creditassistant.knowledge.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer textNormalizer = new TextNormalizer();

    @Test
    void shouldUnifyLineEndingsAndRemoveRedundantWhitespaceWhilePreservingParagraphs() {
        String text = "  # Title  \r\n\r\n\r\n\r\n## Section\r\nFirst   line\t\twith  spaces.   \rSecond line.\n\n\n";

        assertThat(textNormalizer.normalize(text))
                .isEqualTo("# Title\n\n## Section\nFirst line with spaces.\nSecond line.");
    }

    @Test
    void shouldKeepAlreadyNormalizedTextUnchanged() {
        String text = "## Early repayment fees\nNo additional fee is charged.\n\n## Next section\nText.";

        assertThat(textNormalizer.normalize(text)).isEqualTo(text);
    }
}
