package com.corebanking.ledger.infrastructure;

import com.corebanking.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByTransferIdOrderByCreatedAtAsc(String transferId);
}
