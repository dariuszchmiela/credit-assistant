package pl.dch.creditassistant.mcp.infrastructure;

import pl.dch.creditassistant.credit.contract.domain.CreditContract;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Structured MCP output of {@code getContractStatus}. Unknown contracts are reported with {@code found=false};
 * a missing next payment is an explicit {@code null}.
 */
record ContractStatusResult(
        String contractNumber,
        boolean found,
        String status,
        BigDecimal outstandingPrincipal,
        String nextPaymentDate
) {

    static ContractStatusResult of(CreditContract contract) {
        return new ContractStatusResult(
                contract.contractNumber(),
                true,
                contract.status().name(),
                contract.outstandingPrincipal(),
                contract.nextPaymentDate().map(LocalDate::toString).orElse(null)
        );
    }

    static ContractStatusResult notFound(String contractNumber) {
        return new ContractStatusResult(contractNumber, false, null, null, null);
    }
}
