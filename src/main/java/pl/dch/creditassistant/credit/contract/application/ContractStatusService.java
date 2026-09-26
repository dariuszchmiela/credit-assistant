package pl.dch.creditassistant.credit.contract.application;

import org.springframework.stereotype.Service;
import pl.dch.creditassistant.credit.contract.domain.CreditContract;

import java.util.Optional;

@Service
public class ContractStatusService {

    private final ContractRepository contractRepository;

    public ContractStatusService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    public Optional<CreditContract> findContract(String contractNumber) {
        return contractRepository.findByContractNumber(contractNumber);
    }
}
