package com.corebanking.ledger.service;

import com.corebanking.ledger.dto.LedgerEntryResponse;
import com.corebanking.ledger.entity.LedgerEntry;
import com.corebanking.ledger.repository.LedgerEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerReadService {

    private final com.corebanking.common.cache.AccountCache cache;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerReadService(LedgerEntryRepository ledgerEntryRepository, com.corebanking.common.cache.AccountCache cache) {
        this.cache = cache;
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

    public List<LedgerEntryResponse> getAccountLedgerEntryResponses(String accountId, int limit) {
        return cache.read("accountLedgerEntries", accountId, ":" + Math.max(1, Math.min(limit, 100)),
                () -> getAccountLedgerEntries(accountId, limit)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList());
    }
}
