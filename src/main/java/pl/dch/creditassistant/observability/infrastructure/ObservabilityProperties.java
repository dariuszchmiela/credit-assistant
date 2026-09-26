package pl.dch.creditassistant.observability.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * SPEC 58: typed model pricing configuration. Estimates only, not billing-authoritative.
 */
@ConfigurationProperties("observability")
public record ObservabilityProperties(
        Cost cost
) {

    /**
     * @param inputPerMillionTokens  price of one million input (prompt) tokens
     * @param outputPerMillionTokens price of one million output (completion) tokens
     */
    public record Cost(
            BigDecimal inputPerMillionTokens,
            BigDecimal outputPerMillionTokens
    ) {
    }
}
