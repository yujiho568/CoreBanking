package com.corebanking.openbanking.controller;

import com.corebanking.openbanking.dto.ExternalAccountResponse;
import com.corebanking.openbanking.dto.ExternalBalanceResponse;
import com.corebanking.openbanking.dto.ExternalTransactionsResponse;
import com.corebanking.openbanking.port.TransactionQuery;
import com.corebanking.openbanking.service.ExternalAccountQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/external-accounts")
public class ExternalAccountController {
    private final ExternalAccountQueryService service;

    public ExternalAccountController(ExternalAccountQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ExternalAccountResponse> list(@RequestHeader("X-User-Id") String ownerId) {
        return service.list(requireOwner(ownerId));
    }

    @GetMapping("/{externalAccountId}/balance")
    public ExternalBalanceResponse balance(@RequestHeader("X-User-Id") String ownerId,
                                           @PathVariable String externalAccountId,
                                           @RequestParam(defaultValue = "false") boolean refresh) {
        return service.balance(requireOwner(ownerId), externalAccountId, refresh);
    }

    @GetMapping("/{externalAccountId}/transactions")
    public ExternalTransactionsResponse transactions(
            @RequestHeader("X-User-Id") String ownerId,
            @PathVariable String externalAccountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "A") String inquiryType,
            @RequestParam(defaultValue = "D") String sortOrder,
            @RequestParam(defaultValue = "1") int maxPages,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        if (!List.of("A", "I", "O").contains(inquiryType)) {
            throw new IllegalArgumentException("inquiryType must be A, I, or O.");
        }
        if (!List.of("A", "D").contains(sortOrder)) {
            throw new IllegalArgumentException("sortOrder must be A or D.");
        }
        TransactionQuery query = new TransactionQuery(fromDate, toDate, inquiryType, sortOrder, null);
        return service.transactions(requireOwner(ownerId), externalAccountId, query, maxPages, refresh);
    }

    private String requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("X-User-Id must not be blank.");
        return ownerId;
    }
}
