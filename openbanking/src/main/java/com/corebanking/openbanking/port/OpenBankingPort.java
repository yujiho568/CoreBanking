package com.corebanking.openbanking.port;

import java.net.URI;

public interface OpenBankingPort {
    URI authorizationUri(String state);
    TokenBundle exchangeAuthorizationCode(String code);
    TokenBundle refreshToken(String refreshToken);
    UserAccounts getUserAccounts(String accessToken, String userSeqNo);
    BalanceInfo getBalance(String accessToken, String fintechUseNum);
    TransactionPage getTransactions(String accessToken, String fintechUseNum, TransactionQuery query);
}
