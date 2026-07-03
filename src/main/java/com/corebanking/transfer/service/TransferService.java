package com.corebanking.transfer.service;

import com.corebanking.account.dto.AccountTransferResult;
import com.corebanking.account.service.AccountService;
import com.corebanking.common.event.TransferCompletedEvent;
import com.corebanking.ledger.service.LedgerOperations;
import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.Transfer;
import com.corebanking.transfer.exception.TransferNotFoundException;
import com.corebanking.transfer.repository.TransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountService accountService;
    private final LedgerOperations ledgerOperations;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(
            TransferRepository transferRepository,
            AccountService accountService,
            LedgerOperations ledgerOperations,
            ApplicationEventPublisher eventPublisher
    ) {
        this.transferRepository = transferRepository;
        this.accountService = accountService;
        this.ledgerOperations = ledgerOperations;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Transfer execute(CreateTransferRequest request) {
        return transferRepository.findByIdempotencyKey(request.idempotencyKey())
                .orElseGet(() -> executeNewTransfer(request));
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(String transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    private Transfer executeNewTransfer(CreateTransferRequest request) {
        Transfer transfer = Transfer.requested(
                UUID.randomUUID().toString(),
                request.idempotencyKey(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount()
        );
        transferRepository.save(transfer);

        transfer.markProcessing();
        accountService.reserve(request.fromAccountId(), request.amount());
        AccountTransferResult balances = accountService.commitTransfer(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount()
        );
        ledgerOperations.record(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                transfer.getTransferId(),
                balances.fromBalanceAfter(),
                balances.toBalanceAfter()
        );
        transfer.complete();

        eventPublisher.publishEvent(new TransferCompletedEvent(
                transfer.getTransferId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount()
        ));
        return transfer;
    }
}
