package com.corebanking.common.event;

public final class Topics {
    public static final String TRANSFER_CREATED   = "transfer.created";
    public static final String TRANSFER_COMPLETED = "transfer.completed";
    public static final String TRANSFER_CANCELLED = "transfer.cancelled";
    public static final String ACCOUNT_RESERVED   = "account.reserved";
    public static final String ACCOUNT_FAILED     = "account.failed";
    public static final String LEDGER_RECORDED    = "ledger.recorded";
    public static final String LEDGER_FAILED      = "ledger.failed";

    private Topics() {}
}
