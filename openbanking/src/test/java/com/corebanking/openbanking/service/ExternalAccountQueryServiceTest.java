package com.corebanking.openbanking.service;

import com.corebanking.openbanking.dto.ExternalTransactionsResponse;
import com.corebanking.openbanking.entity.ExternalAccount;
import com.corebanking.openbanking.entity.OpenBankingConnection;
import com.corebanking.openbanking.exception.ExternalAccountAccessDeniedException;
import com.corebanking.openbanking.port.*;
import com.corebanking.openbanking.repository.ExternalAccountRepository;
import com.corebanking.openbanking.repository.OpenBankingConnectionRepository;
import com.corebanking.openbanking.security.SensitiveValueCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalAccountQueryServiceTest {
    @Mock ExternalAccountRepository accounts;
    @Mock OpenBankingConnectionRepository connections;
    @Mock OpenBankingTokenService tokens;
    @Mock OpenBankingPort port;
    @Mock SensitiveValueCipher cipher;

    private ExternalAccountQueryService service;
    private ExternalAccount account;
    private OpenBankingConnection connection;

    @BeforeEach
    void setUp() {
        service = new ExternalAccountQueryService(accounts, connections, tokens, port, cipher);
        connection = OpenBankingConnection.connect("owner-1", "1001", "encrypted-access",
                "encrypted-refresh", Instant.now().plusSeconds(3600), "inquiry");
        account = ExternalAccount.connect(connection.getConnectionId(), "owner-1", "encrypted-fintech",
                "fingerprint", "097", "오픈은행", "생활비", "110-***-1234", true);
    }

    @Test
    void fetchesTransactionPagesUpToTheRequestedLimit() {
        when(accounts.findById(account.getExternalAccountId())).thenReturn(Optional.of(account));
        when(connections.findById(connection.getConnectionId())).thenReturn(Optional.of(connection));
        when(tokens.validAccessToken(connection)).thenReturn("access-token");
        when(cipher.decrypt("encrypted-fintech")).thenReturn("123456789012345678901234");

        ExternalTransactionInfo first = transaction("2026-11-19T10:00:00", "1000");
        ExternalTransactionInfo second = transaction("2026-11-20T11:00:00", "2000");
        when(port.getTransactions(eq("access-token"), anyString(), any(TransactionQuery.class)))
                .thenReturn(new TransactionPage("api-1", List.of(first), true, "trace-1"))
                .thenReturn(new TransactionPage("api-2", List.of(second), false, "trace-2"));

        TransactionQuery query = new TransactionQuery(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 20), "A", "D", null);
        ExternalTransactionsResponse response = service.transactions(
                "owner-1", account.getExternalAccountId(), query, 5);

        assertThat(response.transactions()).containsExactly(first, second);
        assertThat(response.pagesFetched()).isEqualTo(2);
        assertThat(response.hasMore()).isFalse();
        ArgumentCaptor<TransactionQuery> queries = ArgumentCaptor.forClass(TransactionQuery.class);
        verify(port, times(2)).getTransactions(eq("access-token"), anyString(), queries.capture());
        assertThat(queries.getAllValues().get(1).beforeInquiryTraceInfo()).isEqualTo("trace-1");
    }

    @Test
    void stopsAtMaxPagesAndReturnsTheContinuationTrace() {
        when(accounts.findById(account.getExternalAccountId())).thenReturn(Optional.of(account));
        when(connections.findById(connection.getConnectionId())).thenReturn(Optional.of(connection));
        when(tokens.validAccessToken(connection)).thenReturn("access-token");
        when(cipher.decrypt(anyString())).thenReturn("fintech-number");
        when(port.getTransactions(anyString(), anyString(), any(TransactionQuery.class)))
                .thenReturn(new TransactionPage("api-1", List.of(), true, "continue-here"));

        ExternalTransactionsResponse response = service.transactions("owner-1", account.getExternalAccountId(),
                new TransactionQuery(LocalDate.now().minusDays(1), LocalDate.now(), "A", "D", null), 1);

        assertThat(response.pagesFetched()).isEqualTo(1);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextTraceInfo()).isEqualTo("continue-here");
        verify(port, times(1)).getTransactions(anyString(), anyString(), any(TransactionQuery.class));
    }

    @Test
    void rejectsAnotherUsersAccountBeforeCallingOpenBanking() {
        when(accounts.findById(account.getExternalAccountId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.balance("attacker", account.getExternalAccountId()))
                .isInstanceOf(ExternalAccountAccessDeniedException.class);

        verifyNoInteractions(tokens, port);
        verify(connections, never()).findById(anyString());
    }

    private ExternalTransactionInfo transaction(String occurredAt, String amount) {
        return new ExternalTransactionInfo(LocalDateTime.parse(occurredAt), "출금", "대체", "테스트",
                new BigDecimal(amount), new BigDecimal("10000"), "분당점");
    }
}
