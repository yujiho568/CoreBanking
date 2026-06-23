package com.corebanking.ledger.application;

import com.corebanking.ledger.domain.LedgerEntry;
import com.corebanking.ledger.infrastructure.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LedgerService implements LedgerOperations {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Override
    public void record(
            String fromAccountId,
            String toAccountId,
            BigDecimal amount,
            String transferId,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter
    ) {
        ledgerEntryRepository.save(LedgerEntry.debit(transferId, fromAccountId, amount, fromBalanceAfter));
        ledgerEntryRepository.save(LedgerEntry.credit(transferId, toAccountId, amount, toBalanceAfter));
    }
}
