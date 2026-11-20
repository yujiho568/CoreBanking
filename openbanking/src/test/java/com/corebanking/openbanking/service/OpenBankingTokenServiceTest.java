package com.corebanking.openbanking.service;

import com.corebanking.openbanking.entity.OpenBankingConnection;
import com.corebanking.openbanking.entity.OpenBankingConnectionStatus;
import com.corebanking.openbanking.exception.OpenBankingProviderException;
import com.corebanking.openbanking.exception.OpenBankingReauthorizationRequiredException;
import com.corebanking.openbanking.port.OpenBankingPort;
import com.corebanking.openbanking.port.TokenBundle;
import com.corebanking.openbanking.repository.OpenBankingConnectionRepository;
import com.corebanking.openbanking.security.SensitiveValueCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenBankingTokenServiceTest {
    @Mock OpenBankingPort port;
    @Mock OpenBankingConnectionRepository connections;
    @Mock SensitiveValueCipher cipher;

    private OpenBankingTokenService service;
    private OpenBankingConnection expired;

    @BeforeEach
    void setUp() {
        service = new OpenBankingTokenService(port, connections, cipher);
        expired = OpenBankingConnection.connect("owner", "1001", "old-access", "old-refresh",
                Instant.now().minusSeconds(1), "inquiry");
        when(cipher.decrypt("old-refresh")).thenReturn("refresh-token");
    }

    @Test
    void refreshesAnExpiredAccessToken() {
        TokenBundle refreshed = new TokenBundle("new-access", "new-refresh",
                Instant.now().plusSeconds(3600), "inquiry", null);
        when(port.refreshToken("refresh-token")).thenReturn(refreshed);
        when(cipher.encrypt("new-access")).thenReturn("encrypted-new-access");
        when(cipher.encrypt("new-refresh")).thenReturn("encrypted-new-refresh");

        assertThat(service.validAccessToken(expired)).isEqualTo("new-access");
        assertThat(expired.getStatus()).isEqualTo(OpenBankingConnectionStatus.ACTIVE);
        verify(connections).save(expired);
    }

    @Test
    void transientProviderFailureDoesNotInvalidateTheConnection() {
        when(port.refreshToken("refresh-token")).thenThrow(OpenBankingProviderException.timeout());

        assertThatThrownBy(() -> service.validAccessToken(expired))
                .isInstanceOf(OpenBankingProviderException.class);
        assertThat(expired.getStatus()).isEqualTo(OpenBankingConnectionStatus.ACTIVE);
        verify(connections, never()).save(any());
    }

    @Test
    void rejectedRefreshTokenRequiresAuthorizationAgain() {
        when(port.refreshToken("refresh-token"))
                .thenThrow(new OpenBankingReauthorizationRequiredException());

        assertThatThrownBy(() -> service.validAccessToken(expired))
                .isInstanceOf(OpenBankingReauthorizationRequiredException.class);
        assertThat(expired.getStatus()).isEqualTo(OpenBankingConnectionStatus.REAUTHORIZATION_REQUIRED);
        verify(connections).save(expired);
    }
}
