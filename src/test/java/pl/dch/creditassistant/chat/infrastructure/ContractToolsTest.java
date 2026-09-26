package pl.dch.creditassistant.chat.infrastructure;

import org.junit.jupiter.api.Test;
import pl.dch.creditassistant.credit.contract.application.ContractStatusService;
import pl.dch.creditassistant.credit.contract.infrastructure.InMemoryContractRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-003: the tool adapter exposes all deterministic contract fields to the model.
 */
class ContractToolsTest {

    private final ContractTools contractTools =
            new ContractTools(new ContractStatusService(new InMemoryContractRepository()));

    @Test
    void shouldDescribeContractWithNextPayment() {
        assertThat(contractTools.getContractStatus("CTR-1001")).isEqualTo(
                "contractNumber=CTR-1001, status=ACTIVE, outstandingPrincipal=85000.00, nextPaymentDate=2026-10-15");
    }

    @Test
    void shouldMarkMissingNextPaymentExplicitly() {
        assertThat(contractTools.getContractStatus("CTR-1002")).isEqualTo(
                "contractNumber=CTR-1002, status=PAID_OFF, outstandingPrincipal=0.00, nextPaymentDate=NONE");
    }

    @Test
    void shouldReportUnknownContract() {
        assertThat(contractTools.getContractStatus("CTR-9999")).isEqualTo("NOT_FOUND");
    }
}
