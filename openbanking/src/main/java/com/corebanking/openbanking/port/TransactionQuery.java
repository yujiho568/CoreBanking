package com.corebanking.openbanking.port;

import java.time.LocalDate;

public record TransactionQuery(
        LocalDate fromDate,
        LocalDate toDate,
        String inquiryType,
        String sortOrder,
        String beforeInquiryTraceInfo
) {
    public TransactionQuery next(String traceInfo) {
        return new TransactionQuery(fromDate, toDate, inquiryType, sortOrder, traceInfo);
    }
}
