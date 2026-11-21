package com.corebanking.openbanking.imported;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/datop/datasets")
public class DatopAggregateController {
    private final DatopAggregateService service;
    public DatopAggregateController(DatopAggregateService service){this.service=service;}
    @GetMapping public List<DatopAggregateService.Dataset> list(@RequestHeader("X-User-Id")String owner){return service.list(owner);}
    @GetMapping("/{id}/aggregates") public DatopAggregateService.Page search(@RequestHeader("X-User-Id")String owner,@PathVariable String id,
            @RequestParam String fromMonth,@RequestParam String toMonth,@RequestParam(required=false)String handlingBank,
            @RequestParam(defaultValue="0")long afterId,@RequestParam(defaultValue="100")int limit){
        return service.search(owner,id,fromMonth,toMonth,handlingBank,afterId,limit);
    }
}
