package com.corebanking.openbanking.imported;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
@Component @ConditionalOnProperty(name="core-banking.datop.aggregate.enabled",havingValue="true")
public class DatopAggregateRunner implements ApplicationRunner {
    private final DatopAggregateService service;private final Environment env;
    public DatopAggregateRunner(DatopAggregateService service,Environment env){this.service=service;this.env=env;}
    public void run(ApplicationArguments args)throws Exception{
        var result=service.importFile(Path.of(required("file")),Path.of(required("archive")),required("owner"),required("source-kind"),env.getProperty("core-banking.datop.aggregate.encoding","UTF-8"));
        org.slf4j.LoggerFactory.getLogger(getClass()).info("DATOP aggregate dataset={} rows={} kind={}",result.id(),result.rowCount(),result.sourceKind());
    }
    private String required(String key){return env.getRequiredProperty("core-banking.datop.aggregate."+key);}
}
