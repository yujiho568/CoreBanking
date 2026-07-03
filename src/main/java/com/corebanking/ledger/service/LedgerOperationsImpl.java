package com.corebanking.ledger.service;

import com.corebanking.ledger.entity.LedgerEntry;
import com.corebanking.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LedgerOperationsImpl implements LedgerOperations {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerOperationsImpl(LedgerEntryRepository ledgerEntryRepository) {
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
