package com.corebanking.transfer.service;

import com.corebanking.account.port.AccountTransferPort;
import com.corebanking.account.port.AccountTransferResult;
import com.corebanking.common.event.TransferCompletedEvent;
import com.corebanking.ledger.port.LedgerRecordPort;
import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.Transfer;
import com.corebanking.transfer.repository.TransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferTransactionService {

    private final com.corebanking.common.cache.CacheInvalidationRepository invalidations;
    private final TransferRepository transferRepository;
    private final AccountTransferPort accountTransferPort;
    private final LedgerRecordPort ledgerRecordPort;
    private final ApplicationEventPublisher eventPublisher;

    public TransferTransactionService(
            com.corebanking.common.cache.CacheInvalidationRepository invalidations,
            TransferRepository transferRepository,
            AccountTransferPort accountTransferPort,
            LedgerRecordPort ledgerRecordPort,
            ApplicationEventPublisher eventPublisher
    ) {
        this.invalidations = invalidations;
        this.transferRepository = transferRepository;
        this.accountTransferPort = accountTransferPort;
        this.ledgerRecordPort = ledgerRecordPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Transfer execute(CreateTransferRequest request) {
        return transferRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(t -> validate(t, request))
                .orElseGet(() -> executeNewTransfer(request));
    }

    public Transfer validate(Transfer transfer, CreateTransferRequest request) {
        if (!transfer.getFromAccountId().equals(request.fromAccountId())
                || !transfer.getToAccountId().equals(request.toAccountId())
                || transfer.getAmount().compareTo(request.amount()) != 0
                || transfer.getStatus() != com.corebanking.transfer.entity.TransferStatus.COMPLETED) {
            throw new com.corebanking.transfer.exception.IdempotencyConflictException();
        }
        return transfer;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, readOnly = true)
    public Transfer recover(CreateTransferRequest request) {
        return validate(transferRepository.findByIdempotencyKey(request.idempotencyKey()).orElseThrow(), request);
    }

    private Transfer executeNewTransfer(CreateTransferRequest request) {
        Transfer transfer = Transfer.requested(
                UUID.randomUUID().toString(),
                request.idempotencyKey(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount()
        );
        transferRepository.saveAndFlush(transfer);

        transfer.markProcessing();
        accountTransferPort.lockTransferAccounts(request.fromAccountId(), request.toAccountId());
        accountTransferPort.reserve(request.fromAccountId(), request.amount());
        AccountTransferResult balances = accountTransferPort.commitTransfer(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount()
        );
        ledgerRecordPort.record(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                transfer.getTransferId(),
                balances.fromBalanceAfter(),
                balances.toBalanceAfter()
        );
        transfer.complete();
        invalidations.save(new com.corebanking.common.cache.CacheInvalidation(request.fromAccountId()));
        invalidations.save(new com.corebanking.common.cache.CacheInvalidation(request.toAccountId()));
        transferRepository.save(transfer);

        eventPublisher.publishEvent(new TransferCompletedEvent(
                transfer.getTransferId(),
                transfer.getFromAccountId(),
                transfer.getToAccountId(),
                transfer.getAmount()
        ));
        return transfer;
    }
}
