package com.corebanking.transfer.service;

import com.corebanking.account.repository.AccountRepository;
import com.corebanking.ledger.repository.LedgerEntryRepository;
import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.TransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferServiceIntegrationTest {

    private final TransferService transferService;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    TransferServiceIntegrationTest(
            TransferService transferService,
            AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.transferService = transferService;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Test
    void executeTransferCompletesInSingleTransactionAndIsIdempotent() {
        CreateTransferRequest request = new CreateTransferRequest(
                "idem-test-001",
                "ACC-001",
                "ACC-002",
                new BigDecimal("10000.00")
        );

        var first = transferService.execute(request);
        var second = transferService.execute(request);

        assertThat(second.getTransferId()).isEqualTo(first.getTransferId());
        assertThat(first.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(ledgerEntryRepository.findByTransferIdOrderByCreatedAtAsc(first.getTransferId())).hasSize(2);

        var from = accountRepository.findById("ACC-001").orElseThrow();
        var to = accountRepository.findById("ACC-002").orElseThrow();
        assertThat(from.getBalance()).isEqualByComparingTo("990000.00");
        assertThat(from.getReservedBalance()).isEqualByComparingTo("0.00");
        assertThat(to.getBalance()).isEqualByComparingTo("510000.00");
    }
}
