package pl.dch.creditassistant.credit.contract.application;

import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.credit.contract.domain.ContractStatus;
import pl.dch.creditassistant.credit.contract.domain.CreditContract;
import pl.dch.creditassistant.credit.contract.infrastructure.InMemoryContractRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-003: deterministic contract data without Spring or an LLM.
 */
class ContractStatusServiceTest {

    private final ContractStatusService contractStatusService =
            new ContractStatusService(new InMemoryContractRepository());

    @Test
    void shouldReturnStatusOutstandingPrincipalAndNextPaymentDateOfActiveContract() {
        Optional<CreditContract> contract = contractStatusService.findContract("CTR-1001");

        assertThat(contract).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(ContractStatus.ACTIVE);
            assertThat(found.outstandingPrincipal()).isEqualByComparingTo("85000.00");
            assertThat(found.nextPaymentDate()).contains(LocalDate.of(2026, 10, 15));
        });
    }

    @Test
    void shouldReturnOverdueContractWithOutstandingPrincipal() {
        Optional<CreditContract> contract = contractStatusService.findContract("CTR-1003");

        assertThat(contract).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(ContractStatus.OVERDUE);
            assertThat(found.outstandingPrincipal()).isEqualByComparingTo("12450.50");
            assertThat(found.nextPaymentDate()).contains(LocalDate.of(2026, 9, 15));
        });
    }

    @Test
    void shouldRepresentMissingNextPaymentExplicitlyForPaidOffContract() {
        Optional<CreditContract> contract = contractStatusService.findContract("CTR-1002");

        assertThat(contract).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(ContractStatus.PAID_OFF);
            assertThat(found.outstandingPrincipal()).isEqualByComparingTo("0");
            assertThat(found.nextPaymentDate()).isEmpty();
        });
    }

    @Test
    void shouldReturnEmptyForUnknownContract() {
        assertThat(contractStatusService.findContract("CTR-9999")).isEmpty();
    }
}
