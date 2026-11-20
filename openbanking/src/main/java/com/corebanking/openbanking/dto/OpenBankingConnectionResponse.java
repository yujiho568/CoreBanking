package com.corebanking.openbanking.dto;

import java.util.List;

public record OpenBankingConnectionResponse(
        String connectionId,
        String userSeqNo,
        List<ExternalAccountResponse> accounts
) {
}
