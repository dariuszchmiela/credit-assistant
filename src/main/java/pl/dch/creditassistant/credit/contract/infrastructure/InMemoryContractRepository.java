package pl.dch.creditassistant.credit.contract.infrastructure;

import org.springframework.stereotype.Repository;
import pl.dch.creditassistant.credit.contract.application.ContractRepository;
import pl.dch.creditassistant.credit.contract.domain.ContractStatus;
import pl.dch.creditassistant.credit.contract.domain.CreditContract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryContractRepository implements ContractRepository {

    private final Map<String, CreditContract> contracts = Map.of(
            "CTR-1001", new CreditContract(
                    "CTR-1001",
                    ContractStatus.ACTIVE,
                    new BigDecimal("85000.00"),
                    Optional.of(LocalDate.of(2026, 10, 15))
            ),
            "CTR-1002", new CreditContract(
                    "CTR-1002",
                    ContractStatus.PAID_OFF,
                    new BigDecimal("0.00"),
                    Optional.empty()
            ),
            "CTR-1003", new CreditContract(
                    "CTR-1003",
                    ContractStatus.OVERDUE,
                    new BigDecimal("12450.50"),
                    Optional.of(LocalDate.of(2026, 9, 15))
            ),
            "CTR-1004", new CreditContract(
                    "CTR-1004",
                    ContractStatus.CANCELLED,
                    new BigDecimal("0.00"),
                    Optional.empty()
            )
    );

    @Override
    public Optional<CreditContract> findByContractNumber(String contractNumber) {
        return Optional.ofNullable(contracts.get(contractNumber));
    }
}
