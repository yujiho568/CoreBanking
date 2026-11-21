package com.corebanking.openbanking.controller;

import com.corebanking.openbanking.config.OpenBankingProperties;
import com.corebanking.openbanking.service.OpenBankingOAuthService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

/** Convenience entry point for fake accounts; absent outside the mock profile. */
@Profile("mock")
@RestController
public class MockDemoController {
    private final OpenBankingOAuthService oauth;
    public MockDemoController(OpenBankingOAuthService oauth, OpenBankingProperties properties) {
        if (!URI.create("http://127.0.0.1:9099").equals(properties.baseUrl()) ||
                !"mock-client".equals(properties.clientId()) || !"mock-secret".equals(properties.clientSecret()))
            throw new IllegalStateException("Mock demo must use the loopback simulator and dummy credentials.");
        this.oauth = oauth;
    }
    @GetMapping("/api/mock/connect")
    public ResponseEntity<Void> connect() {
        return ResponseEntity.status(302).location(oauth.begin("mock-demo-user")).build();
    }
    @GetMapping(value="/mock-demo", produces=MediaType.TEXT_HTML_VALUE)
    public Resource page() { return new ClassPathResource("mock-demo.html"); }
}
