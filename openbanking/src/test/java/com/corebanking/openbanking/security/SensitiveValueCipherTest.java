package com.corebanking.openbanking.security;

import com.corebanking.openbanking.config.OpenBankingProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsTheOriginalValue() {
        String key = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        OpenBankingProperties properties = new OpenBankingProperties(
                URI.create("https://testapi.openbanking.or.kr"),
                URI.create("https://testapi.openbanking.or.kr/oauth/2.0/authorize"),
                "client", "secret", "F123456789", URI.create("http://localhost/callback"),
                "login inquiry", "0", Duration.ofSeconds(1), Duration.ofSeconds(1), key);
        SensitiveValueCipher cipher = new SensitiveValueCipher(properties);

        String first = cipher.encrypt("sensitive-value");
        String second = cipher.encrypt("sensitive-value");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("sensitive-value");
        assertThat(cipher.fingerprint("sensitive-value")).hasSize(64);
    }
}
