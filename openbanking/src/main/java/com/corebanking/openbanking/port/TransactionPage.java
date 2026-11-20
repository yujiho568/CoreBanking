package com.corebanking.openbanking.port;

import java.util.List;

public record TransactionPage(
        String apiTransactionId,
        List<ExternalTransactionInfo> transactions,
        boolean hasNext,
        String nextTraceInfo
) {
}
