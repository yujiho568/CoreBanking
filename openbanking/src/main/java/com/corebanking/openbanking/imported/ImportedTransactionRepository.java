package com.corebanking.openbanking.imported;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
public interface ImportedTransactionRepository extends JpaRepository<ImportedTransaction,Long> {
    @Query("select t from ImportedTransaction t where t.datasetId=:dataset and t.accountRef=:account and t.occurredAt>=:from and t.occurredAt<:until and (t.occurredAt>:afterTime or (t.occurredAt=:afterTime and t.id>:afterId)) order by t.occurredAt,t.id")
    List<ImportedTransaction> search(String dataset, String account, LocalDateTime from, LocalDateTime until,
                                   LocalDateTime afterTime, long afterId, Pageable pageable);
}
