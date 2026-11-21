package com.corebanking.openbanking.imported;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
@RestController
@RequestMapping("/api/imported-datasets")
public class ImportedTransactionController {
    private final ImportedQueryService service;
    public ImportedTransactionController(ImportedQueryService service) { this.service=service; }
    @GetMapping("/{dataset}/transactions")
    public ImportedQueryService.Page search(@RequestHeader("X-User-Id") String owner, @PathVariable String dataset,
        @RequestParam String account,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime until,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime afterTime,
        @RequestParam(defaultValue="0") long afterId, @RequestParam(defaultValue="100") int limit,
        @RequestParam(defaultValue="false") boolean refresh) {
        return service.search(owner,dataset,account,from,until,afterTime,afterId,limit,refresh);
    }
}
