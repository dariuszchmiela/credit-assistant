package pl.dch.creditassistant.evaluation;

/**
 * SPEC 71: the scenarios the evaluation set must cover.
 */
enum EvaluationCategory {
    FAQ_RETRIEVAL,
    SEMANTIC_EARLY_REPAYMENT,
    CONTRACT_STATUS_TOOL,
    INSTALLMENT_CALCULATION_TOOL,
    ELIGIBLE_CUSTOMER,
    INELIGIBLE_CUSTOMER,
    COMBINED_RAG_AND_TOOL,
    PII_MASKING,
    UNSUPPORTED_FUTURE_INFORMATION,
    MISSING_PRODUCT_KNOWLEDGE
}
