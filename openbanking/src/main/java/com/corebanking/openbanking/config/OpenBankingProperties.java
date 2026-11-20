package com.corebanking.openbanking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "core-banking.open-banking")
public record OpenBankingProperties(
        URI baseUrl,
        URI authorizationUrl,
        String clientId,
        String clientSecret,
        String clientUseCode,
        URI redirectUri,
        String scopes,
        String authType,
        Duration connectTimeout,
        Duration readTimeout,
        String encryptionKey
) {
    public void requireClientCredentials() {
        requireText(clientId, "OPEN_BANKING_CLIENT_ID");
        requireText(clientSecret, "OPEN_BANKING_CLIENT_SECRET");
    }

    public void requireInquiryConfiguration() {
        requireClientCredentials();
        requireText(clientUseCode, "OPEN_BANKING_CLIENT_USE_CODE");
        if (clientUseCode.length() != 10) {
            throw new IllegalStateException("OPEN_BANKING_CLIENT_USE_CODE must be 10 characters.");
        }
    }

    private static void requireText(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentName + " is required for Open Banking calls.");
        }
    }
}
