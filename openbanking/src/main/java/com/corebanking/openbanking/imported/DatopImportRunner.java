package com.corebanking.openbanking.imported;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
@Component
@ConditionalOnProperty(name="core-banking.datop.enabled",havingValue="true")
public class DatopImportRunner implements ApplicationRunner {
    private final DatopImportService service;
    private final Environment env;
    public DatopImportRunner(DatopImportService service, Environment env) { this.service=service; this.env=env; }
    public void run(ApplicationArguments args) throws Exception {
        ImportedDataset result=service.importFile(Path.of(required("source")),Path.of(required("normalized")),Path.of(required("archive")),
                required("owner"),required("label"),synthetic());
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Imported dataset={} rows={} synthetic={}",
                result.id,result.rowCount,result.synthetic);
    }
    private boolean synthetic() {
        String value=required("synthetic");
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("synthetic must be true or false");
        return Boolean.parseBoolean(value);
    }
    private String required(String key) { return env.getRequiredProperty("core-banking.datop."+key); }
}
