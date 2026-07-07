package com.corebanking.ledger.controller;

import com.corebanking.ledger.dto.LedgerEntryResponse;
import com.corebanking.ledger.service.LedgerReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class LedgerController {

    private final LedgerReadService ledgerReadService;

    public LedgerController(LedgerReadService ledgerReadService) {
        this.ledgerReadService = ledgerReadService;
    }

    @GetMapping("/{accountId}/ledger-entries")
    public List<LedgerEntryResponse> getAccountLedgerEntries(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ledgerReadService.getAccountLedgerEntries(accountId, limit)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }
}
