package com.corebanking.openbanking.service;

import com.corebanking.openbanking.entity.OpenBankingConnection;
import com.corebanking.openbanking.entity.OpenBankingConnectionStatus;
import com.corebanking.openbanking.exception.OpenBankingReauthorizationRequiredException;
import com.corebanking.openbanking.port.OpenBankingPort;
import com.corebanking.openbanking.port.TokenBundle;
import com.corebanking.openbanking.repository.OpenBankingConnectionRepository;
import com.corebanking.openbanking.security.SensitiveValueCipher;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class OpenBankingTokenService {
    private final OpenBankingPort port;
    private final OpenBankingConnectionRepository connections;
    private final SensitiveValueCipher cipher;

    public OpenBankingTokenService(OpenBankingPort port, OpenBankingConnectionRepository connections,
                                   SensitiveValueCipher cipher) {
        this.port = port;
        this.connections = connections;
        this.cipher = cipher;
    }

    public String validAccessToken(OpenBankingConnection connection) {
        if (connection.getStatus() != OpenBankingConnectionStatus.ACTIVE) {
            throw new OpenBankingReauthorizationRequiredException();
        }
        if (connection.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cipher.decrypt(connection.getAccessTokenCiphertext());
        }
        String encryptedRefreshToken = connection.getRefreshTokenCiphertext();
        if (encryptedRefreshToken == null) {
            connection.requireReauthorization();
            connections.save(connection);
            throw new OpenBankingReauthorizationRequiredException();
        }
        TokenBundle refreshed;
        try {
            refreshed = port.refreshToken(cipher.decrypt(encryptedRefreshToken));
        } catch (OpenBankingReauthorizationRequiredException failure) {
            connection.requireReauthorization();
            connections.save(connection);
            throw failure;
        }
        connection.updateTokens(cipher.encrypt(refreshed.accessToken()), cipher.encrypt(refreshed.refreshToken()),
                refreshed.expiresAt(), refreshed.scope());
        connections.save(connection);
        return refreshed.accessToken();
    }
}
