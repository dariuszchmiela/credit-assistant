package pl.dch.creditassistant.credit.contract.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record CreditContract(
        String contractNumber,
        ContractStatus status,
        BigDecimal outstandingPrincipal,
        Optional<LocalDate> nextPaymentDate
) {

    public CreditContract {
        Objects.requireNonNull(contractNumber, "contractNumber is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(outstandingPrincipal, "outstandingPrincipal is required");
        Objects.requireNonNull(nextPaymentDate, "nextPaymentDate must be Optional.empty() when there is no next payment");
    }
}
