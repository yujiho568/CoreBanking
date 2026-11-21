package com.corebanking.openbanking.imported;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface DatopAggregateRepository extends JpaRepository<DatopAggregate,Long> {
    @Query("select r from DatopAggregate r where r.datasetId=:dataset and r.baseYm>=:fromMonth and r.baseYm<=:toMonth and (:bank is null or r.handlingBankCode=:bank) and r.id>:afterId order by r.id")
    List<DatopAggregate> search(String dataset,String fromMonth,String toMonth,String bank,long afterId,Pageable page);
}
