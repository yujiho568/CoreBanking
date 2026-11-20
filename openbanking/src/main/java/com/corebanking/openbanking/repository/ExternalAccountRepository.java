package com.corebanking.openbanking.repository;

import com.corebanking.openbanking.entity.ExternalAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, String> {
    List<ExternalAccount> findAllByOwnerIdOrderByCreatedAtAsc(String ownerId);
    Optional<ExternalAccount> findByConnectionIdAndFintechUseNumHash(String connectionId, String fintechUseNumHash);
}
