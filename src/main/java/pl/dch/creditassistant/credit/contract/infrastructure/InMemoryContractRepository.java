package pl.dch.creditassistant.credit.contract.infrastructure;

import org.springframework.stereotype.Repository;
import pl.dch.creditassistant.credit.contract.application.ContractRepository;
import pl.dch.creditassistant.credit.contract.domain.ContractStatus;
import pl.dch.creditassistant.credit.contract.domain.CreditContract;

import java.util.Map;
import java.util.Optional;

@Repository
public class InMemoryContractRepository implements ContractRepository {

    private final Map<String, CreditContract> contracts = Map.of(
            "CTR-1001", new CreditContract("CTR-1001", ContractStatus.ACTIVE),
            "CTR-1002", new CreditContract("CTR-1002", ContractStatus.CLOSED),
            "CTR-1003", new CreditContract("CTR-1003", ContractStatus.OVERDUE)
    );

    @Override
    public Optional<CreditContract> findByContractNumber(String contractNumber) {
        return Optional.ofNullable(contracts.get(contractNumber));
    }
}