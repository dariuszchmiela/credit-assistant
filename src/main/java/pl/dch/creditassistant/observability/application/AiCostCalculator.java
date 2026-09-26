package pl.dch.creditassistant.observability.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

/**
 * FR-008, SPEC 57: deterministic estimated cost from token usage and configured prices per million tokens.
 * The result has no currency; it is in whatever unit the prices are configured in.
 */
public class AiCostCalculator {

    static final int COST_SCALE = 10;
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final BigDecimal inputPricePerMillionTokens;
    private final BigDecimal outputPricePerMillionTokens;

    public AiCostCalculator(BigDecimal inputPricePerMillionTokens, BigDecimal outputPricePerMillionTokens) {
        this.inputPricePerMillionTokens = requireNonNegative(inputPricePerMillionTokens, "inputPricePerMillionTokens");
        this.outputPricePerMillionTokens = requireNonNegative(outputPricePerMillionTokens, "outputPricePerMillionTokens");
    }

    /**
     * @return the estimated cost rounded half-up to {@value #COST_SCALE} decimal places,
     *         or empty when the token usage is unknown
     */
    public Optional<BigDecimal> estimate(Integer inputTokenCount, Integer outputTokenCount) {
        if (inputTokenCount == null || outputTokenCount == null) {
            return Optional.empty();
        }

        BigDecimal inputCost = tokenCost(inputTokenCount, inputPricePerMillionTokens);
        BigDecimal outputCost = tokenCost(outputTokenCount, outputPricePerMillionTokens);

        return Optional.of(inputCost.add(outputCost).setScale(COST_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal tokenCost(int tokenCount, BigDecimal pricePerMillionTokens) {
        return BigDecimal.valueOf(tokenCount)
                .multiply(pricePerMillionTokens)
                .divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal requireNonNegative(BigDecimal price, String name) {
        Objects.requireNonNull(price, name + " is required");
        if (price.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return price;
    }
}
