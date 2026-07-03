package com.corebanking.transfer.controller;

import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.dto.TransferResponse;
import com.corebanking.transfer.service.TransferService;
import com.corebanking.transfer.entity.Transfer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody CreateTransferRequest request) {
        Transfer transfer = transferService.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransferResponse.from(transfer));
    }

    @GetMapping("/{transferId}")
    public TransferResponse getTransfer(@PathVariable String transferId) {
        return TransferResponse.from(transferService.getTransfer(transferId));
    }
}
