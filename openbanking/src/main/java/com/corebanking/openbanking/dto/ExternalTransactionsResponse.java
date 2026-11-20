package com.corebanking.openbanking.dto;

import com.corebanking.openbanking.port.ExternalTransactionInfo;

import java.time.Instant;
import java.util.List;

public record ExternalTransactionsResponse(
        String externalAccountId,
        List<ExternalTransactionInfo> transactions,
        int pagesFetched,
        boolean hasMore,
        String nextTraceInfo,
        Instant fetchedAt
) {
}
