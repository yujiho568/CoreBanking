package com.corebanking.openbanking.repository;

import com.corebanking.openbanking.entity.OAuthState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OAuthStateRepository extends JpaRepository<OAuthState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from OAuthState state where state.state = :value")
    Optional<OAuthState> findByIdForUpdate(String value);
}
