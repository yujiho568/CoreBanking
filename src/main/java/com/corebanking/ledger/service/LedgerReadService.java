package com.corebanking.ledger.service;

import com.corebanking.ledger.entity.LedgerEntry;
import com.corebanking.ledger.repository.LedgerEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerReadService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerReadService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getTransferLedgerEntries(String transferId) {
        return ledgerEntryRepository.findByTransferIdOrderByCreatedAtAsc(transferId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountLedgerEntries(String accountId, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, size));
    }
}
