package pl.dch.creditassistant.credit.contract.application;

import pl.dch.creditassistant.credit.contract.domain.CreditContract;

import java.util.Optional;

public interface ContractRepository {

    Optional<CreditContract> findByContractNumber(String contractNumber);
}