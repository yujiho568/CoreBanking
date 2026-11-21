package com.corebanking.openbanking.imported;
import com.corebanking.openbanking.service.ExternalQueryCache;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
@Service
public class ImportedQueryService {
    private final ImportedDatasetRepository datasets;
    private final ImportedTransactionRepository rows;
    private final ExternalQueryCache cache;
    public ImportedQueryService(ImportedDatasetRepository datasets, ImportedTransactionRepository rows, ExternalQueryCache cache) {
        this.datasets=datasets; this.rows=rows; this.cache=cache;
    }
    public record Row(long id, String accountRef, LocalDateTime occurredAt, BigDecimal amount, String direction, String description) {}
    public record Page(List<Row> rows, boolean hasMore, LocalDateTime nextTime, Long nextId, Instant fetchedAt) {}
    public Page search(String owner, String dataset, String account, LocalDateTime from, LocalDateTime until,
                       LocalDateTime afterTime, long afterId, int limit, boolean refresh) {
        if (owner.isBlank() || account.isBlank() || !from.isBefore(until) || limit<1 || limit>500 || afterId<0)
            throw new IllegalArgumentException("Invalid search parameters");
        ImportedDataset source=datasets.findById(dataset).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!source.ownerId.equals(owner)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        LocalDateTime cursor=afterTime==null ? from : afterTime;
        return cache.read("importedTransactions",ExternalQueryCache.key(owner,dataset,account,from,until,cursor,afterId,limit),refresh,()-> {
            List<ImportedTransaction> found=rows.search(dataset,account,from,until,cursor,afterId,PageRequest.of(0,limit+1));
            boolean more=found.size()>limit;
            List<Row> result=found.stream().limit(limit).map(t->new Row(t.id,t.accountRef,t.occurredAt,t.amount,t.direction,t.description)).toList();
            Row last=result.isEmpty()?null:result.get(result.size()-1);
            return new Page(result,more,last==null?null:last.occurredAt(),last==null?null:last.id(),Instant.now());
        });
    }
}
