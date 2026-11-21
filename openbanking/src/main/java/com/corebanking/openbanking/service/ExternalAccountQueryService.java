package com.corebanking.openbanking.service;

import com.corebanking.openbanking.dto.*;
import com.corebanking.openbanking.entity.ExternalAccount;
import com.corebanking.openbanking.entity.OpenBankingConnection;
import com.corebanking.openbanking.exception.ExternalAccountAccessDeniedException;
import com.corebanking.openbanking.exception.ExternalAccountNotFoundException;
import com.corebanking.openbanking.exception.OpenBankingReauthorizationRequiredException;
import com.corebanking.openbanking.port.*;
import com.corebanking.openbanking.repository.ExternalAccountRepository;
import com.corebanking.openbanking.repository.OpenBankingConnectionRepository;
import com.corebanking.openbanking.security.SensitiveValueCipher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExternalAccountQueryService {
    private final ExternalAccountRepository accounts;
    private final OpenBankingConnectionRepository connections;
    private final OpenBankingTokenService tokenService;
    private final OpenBankingPort port;
    private final SensitiveValueCipher cipher;
    private final ExternalQueryCache cache;

    public ExternalAccountQueryService(ExternalAccountRepository accounts,
                                       OpenBankingConnectionRepository connections,
                                       OpenBankingTokenService tokenService, OpenBankingPort port,
                                       SensitiveValueCipher cipher, ExternalQueryCache cache) {
        this.accounts = accounts;
        this.connections = connections;
        this.tokenService = tokenService;
        this.port = port;
        this.cipher = cipher;
        this.cache = cache;
    }

    public List<ExternalAccountResponse> list(String ownerId) {
        return accounts.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(ExternalAccountResponse::from).toList();
    }

    public ExternalBalanceResponse balance(String ownerId, String externalAccountId) {
        return balance(ownerId, externalAccountId, false);
    }

    public ExternalBalanceResponse balance(String ownerId, String externalAccountId, boolean refresh) {
        OwnedAccount owned = getOwned(ownerId, externalAccountId);
        return cache.read("externalBalances", ExternalQueryCache.key(ownerId, externalAccountId,
                owned.connection().getAccessTokenCiphertext()), refresh, () -> {
            String token = tokenService.validAccessToken(owned.connection());
            BalanceInfo balance = port.getBalance(token, cipher.decrypt(owned.account().getFintechUseNumCiphertext()));
            return ExternalBalanceResponse.of(owned.account(), balance);
        });
    }

    public ExternalTransactionsResponse transactions(String ownerId, String externalAccountId,
                                                     TransactionQuery query, int maxPages) {
        return transactions(ownerId, externalAccountId, query, maxPages, false);
    }

    public ExternalTransactionsResponse transactions(String ownerId, String externalAccountId,
                                                     TransactionQuery initialQuery, int maxPages, boolean refresh) {
        if (maxPages < 1 || maxPages > 20) throw new IllegalArgumentException("maxPages must be between 1 and 20.");
        if (initialQuery.fromDate().isAfter(initialQuery.toDate()))
            throw new IllegalArgumentException("fromDate must not be after toDate.");
        if (!List.of("A", "I", "O").contains(initialQuery.inquiryType()) ||
                !List.of("A", "D").contains(initialQuery.sortOrder()))
            throw new IllegalArgumentException("Invalid inquiry type or sort order.");
        OwnedAccount owned = getOwned(ownerId, externalAccountId);
        return cache.read("externalTransactions", ExternalQueryCache.key(ownerId, externalAccountId,
                owned.connection().getAccessTokenCiphertext(), initialQuery, maxPages), refresh,
                () -> fetchTransactions(owned, externalAccountId, initialQuery, maxPages));
    }

    private ExternalTransactionsResponse fetchTransactions(OwnedAccount owned, String externalAccountId,
                                                            TransactionQuery initialQuery, int maxPages) {
        String token = tokenService.validAccessToken(owned.connection());
        String fintechUseNum = cipher.decrypt(owned.account().getFintechUseNumCiphertext());
        List<ExternalTransactionInfo> combined = new ArrayList<>();
        TransactionQuery query = initialQuery;
        TransactionPage page;
        int fetched = 0;
        do {
            page = port.getTransactions(token, fintechUseNum, query);
            combined.addAll(page.transactions());
            fetched++;
            if (page.hasNext()) {
                if (page.nextTraceInfo() == null || page.nextTraceInfo().isBlank())
                    throw new com.corebanking.openbanking.exception.OpenBankingProviderException(
                            "Open Banking indicated another page without a continuation trace.");
                query = query.next(page.nextTraceInfo());
            }
        } while (page.hasNext() && fetched < maxPages);
        return new ExternalTransactionsResponse(externalAccountId, List.copyOf(combined), fetched,
                page.hasNext(), page.nextTraceInfo(), Instant.now());
    }

    private OwnedAccount getOwned(String ownerId, String externalAccountId) {
        ExternalAccount account = accounts.findById(externalAccountId)
                .orElseThrow(() -> new ExternalAccountNotFoundException(externalAccountId));
        if (!account.getOwnerId().equals(ownerId)) throw new ExternalAccountAccessDeniedException();
        if (!account.isInquiryAgreed()) throw new OpenBankingReauthorizationRequiredException();
        OpenBankingConnection connection = connections.findById(account.getConnectionId())
                .orElseThrow(OpenBankingReauthorizationRequiredException::new);
        if (connection.getStatus() != com.corebanking.openbanking.entity.OpenBankingConnectionStatus.ACTIVE)
            throw new OpenBankingReauthorizationRequiredException();
        return new OwnedAccount(account, connection);
    }

    private record OwnedAccount(ExternalAccount account, OpenBankingConnection connection) {}
}
