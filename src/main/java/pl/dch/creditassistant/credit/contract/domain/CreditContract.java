package pl.dch.creditassistant.credit.contract.domain;

public record CreditContract(
        String contractNumber,
        ContractStatus status
) {
}