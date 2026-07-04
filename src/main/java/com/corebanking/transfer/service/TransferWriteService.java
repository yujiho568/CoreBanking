package com.corebanking.transfer.service;

import com.corebanking.account.dto.AccountTransferResult;
import com.corebanking.account.service.AccountWriteService;
import com.corebanking.common.event.TransferCompletedEvent;
import com.corebanking.ledger.service.LedgerWriteService;
import com.corebanking.transfer.dto.CreateTransferRequest;
import com.corebanking.transfer.entity.Transfer;
import com.corebanking.transfer.repository.TransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferWriteService {

    private final TransferRepository transferRepository;
    private final AccountWriteService accountWriteService;
    private final LedgerWriteService ledgerWriteService;
    private final ApplicationEventPublisher eventPublisher;

    public TransferWriteService(
            TransferRepository transferRepository,
            AccountWriteService accountWriteService,
            LedgerWriteService ledgerWriteService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.transferRepository = transferRepository;
        this.accountWriteService = accountWriteService;
        this.ledgerWriteService = ledgerWriteService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Transfer execute(CreateTransferRequest request) {
        return transferRepository.findByIdempotencyKey(request.idempotencyKey())
                .orElseGet(() -> executeNewTransfer(request));
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
        accountWriteService.reserve(request.fromAccountId(), request.amount());
        AccountTransferResult balances = accountWriteService.commitTransfer(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount()
        );
        ledgerWriteService.record(
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
