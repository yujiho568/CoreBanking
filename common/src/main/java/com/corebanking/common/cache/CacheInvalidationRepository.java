package com.corebanking.common.cache;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface CacheInvalidationRepository extends JpaRepository<CacheInvalidation, String> {
    @Query("select c.id from CacheInvalidation c where c.completed = false and c.nextAttempt <= :now order by c.nextAttempt")
    List<String> due(@Param("now") Instant now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CacheInvalidation c where c.id = :id")
    Optional<CacheInvalidation> lock(@Param("id") String id);
    @Query("select count(c), coalesce(sum(case when c.completed = false then 1 else 0 end), 0) from CacheInvalidation c where c.accountId = :account")
    List<Object[]> state(@Param("account") String account);
}
