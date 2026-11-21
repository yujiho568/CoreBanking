package com.corebanking.openbanking;
import com.corebanking.common.config.CacheConfig;
import com.corebanking.openbanking.dto.*;
import com.corebanking.openbanking.port.ExternalTransactionInfo;
import com.corebanking.openbanking.imported.ImportedQueryService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ExternalCacheSerializationTest {
    @Test void redisCodecRoundTripsAllCachedResponses() {
        var codec=new CacheConfig().redisCacheConfiguration(Duration.ofSeconds(10)).getValueSerializationPair();
        var balance=new ExternalBalanceResponse("a","bank","***",BigDecimal.TEN,BigDecimal.ONE,"1","savings",null,"id",Instant.now());
        var transactions=new ExternalTransactionsResponse("a",List.of(new ExternalTransactionInfo(LocalDateTime.now(),"I","transfer","test",BigDecimal.ONE,BigDecimal.TEN,"branch")),1,false,null,Instant.now());
        var imported=new ImportedQueryService.Page(List.of(new ImportedQueryService.Row(1,"acct",LocalDateTime.now(),BigDecimal.ONE,"I","text")),false,null,null,Instant.now());
        for(Object value:List.of(balance,transactions,imported)) assertThat(codec.read(codec.write(value))).isEqualTo(value);
    }
}
