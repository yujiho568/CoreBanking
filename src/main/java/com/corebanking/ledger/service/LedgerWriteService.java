package com.corebanking.ledger.service;

import com.corebanking.ledger.entity.LedgerEntry;
import com.corebanking.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LedgerWriteService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerWriteService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public void record(
            String fromAccountId,
            String toAccountId,
            BigDecimal amount,
            String transferId,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter
    ) {
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.debit(transferId, fromAccountId, amount, fromBalanceAfter),
                LedgerEntry.credit(transferId, toAccountId, amount, toBalanceAfter)
        ));
    }
}
