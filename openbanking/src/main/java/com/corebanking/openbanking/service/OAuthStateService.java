package com.corebanking.openbanking.service;

import com.corebanking.openbanking.entity.OAuthState;
import com.corebanking.openbanking.exception.InvalidOAuthStateException;
import com.corebanking.openbanking.repository.OAuthStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OAuthStateService {
    private final OAuthStateRepository repository;

    public OAuthStateService(OAuthStateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String consume(String state) {
        OAuthState stored = repository.findByIdForUpdate(state).orElseThrow(InvalidOAuthStateException::new);
        if (!stored.canConsume(Instant.now())) throw new InvalidOAuthStateException();
        stored.consume();
        return stored.getOwnerId();
    }
}
