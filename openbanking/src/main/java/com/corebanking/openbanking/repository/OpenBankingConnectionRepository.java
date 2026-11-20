package com.corebanking.openbanking.repository;

import com.corebanking.openbanking.entity.OpenBankingConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpenBankingConnectionRepository extends JpaRepository<OpenBankingConnection, String> {
    Optional<OpenBankingConnection> findByOwnerIdAndUserSeqNo(String ownerId, String userSeqNo);
}
