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

    public ExternalAccountQueryService(ExternalAccountRepository accounts,
                                       OpenBankingConnectionRepository connections,
                                       OpenBankingTokenService tokenService, OpenBankingPort port,
                                       SensitiveValueCipher cipher) {
        this.accounts = accounts;
        this.connections = connections;
        this.tokenService = tokenService;
        this.port = port;
        this.cipher = cipher;
    }

    public List<ExternalAccountResponse> list(String ownerId) {
        return accounts.findAllByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(ExternalAccountResponse::from).toList();
    }

    public ExternalBalanceResponse balance(String ownerId, String externalAccountId) {
        OwnedAccount owned = getOwned(ownerId, externalAccountId);
        String token = tokenService.validAccessToken(owned.connection());
        BalanceInfo balance = port.getBalance(token, cipher.decrypt(owned.account().getFintechUseNumCiphertext()));
        return ExternalBalanceResponse.of(owned.account(), balance);
    }

    public ExternalTransactionsResponse transactions(String ownerId, String externalAccountId,
                                                     TransactionQuery initialQuery, int maxPages) {
        if (maxPages < 1 || maxPages > 20) throw new IllegalArgumentException("maxPages must be between 1 and 20.");
        if (initialQuery.fromDate().isAfter(initialQuery.toDate())) {
            throw new IllegalArgumentException("fromDate must not be after toDate.");
        }
        OwnedAccount owned = getOwned(ownerId, externalAccountId);
        String token = tokenService.validAccessToken(owned.connection());
        String fintechUseNum = cipher.decrypt(owned.account().getFintechUseNumCiphertext());
        List<ExternalTransactionInfo> combined = new ArrayList<>();
        TransactionQuery query = initialQuery;
        TransactionPage page = null;
        int fetched = 0;
        do {
            page = port.getTransactions(token, fintechUseNum, query);
            combined.addAll(page.transactions());
            fetched++;
            if (page.hasNext()) {
                if (page.nextTraceInfo() == null || page.nextTraceInfo().isBlank()) {
                    throw new com.corebanking.openbanking.exception.OpenBankingProviderException(
                            "Open Banking indicated another page without a continuation trace.");
                }
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
        return new OwnedAccount(account, connection);
    }

    private record OwnedAccount(ExternalAccount account, OpenBankingConnection connection) {}
}
