package com.corebanking.openbanking.port;

import java.time.Instant;

public record TokenBundle(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        String scope,
        String userSeqNo
) {
}
