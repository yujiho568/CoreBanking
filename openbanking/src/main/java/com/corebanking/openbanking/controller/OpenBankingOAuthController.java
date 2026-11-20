package com.corebanking.openbanking.controller;

import com.corebanking.openbanking.dto.OpenBankingConnectionResponse;
import com.corebanking.openbanking.service.OpenBankingOAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/open-banking/oauth")
public class OpenBankingOAuthController {
    private final OpenBankingOAuthService service;

    public OpenBankingOAuthController(OpenBankingOAuthService service) {
        this.service = service;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(@RequestHeader("X-User-Id") String ownerId) {
        URI authorizationPage = service.begin(requireOwner(ownerId));
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, authorizationPage.toString()).build();
    }

    @GetMapping("/callback")
    public OpenBankingConnectionResponse callback(@RequestParam String code, @RequestParam String state) {
        return service.complete(code, state);
    }

    private String requireOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("X-User-Id must not be blank.");
        return ownerId;
    }
}
