package com.corebanking.transfer.application;

import com.corebanking.account.application.AccountOperations;
import com.corebanking.account.application.AccountTransferResult;
import com.corebanking.common.event.TransferCompletedEvent;
import com.corebanking.ledger.application.LedgerOperations;
import com.corebanking.transfer.domain.Transfer;
import com.corebanking.transfer.domain.TransferNotFoundException;
import com.corebanking.transfer.infrastructure.TransferRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountOperations accountOperations;
    private final LedgerOperations ledgerOperations;
    private final ApplicationEventPublisher eventPublisher;

    public TransferService(
            TransferRepository transferRepository,
            AccountOperations accountOperations,
            LedgerOperations ledgerOperations,
            ApplicationEventPublisher eventPublisher
    ) {
        this.transferRepository = transferRepository;
        this.accountOperations = accountOperations;
        this.ledgerOperations = ledgerOperations;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Transfer execute(TransferCommand command) {
        return transferRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElseGet(() -> executeNewTransfer(command));
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(String transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    private Transfer executeNewTransfer(TransferCommand command) {
        Transfer transfer = Transfer.requested(
                UUID.randomUUID().toString(),
                command.idempotencyKey(),
                command.fromAccountId(),
                command.toAccountId(),
                command.amount()
        );
        transferRepository.save(transfer);

        transfer.markProcessing();
        accountOperations.reserve(command.fromAccountId(), command.amount());
        AccountTransferResult balances = accountOperations.commitTransfer(
                command.fromAccountId(),
                command.toAccountId(),
                command.amount()
        );
        ledgerOperations.record(
                command.fromAccountId(),
                command.toAccountId(),
                command.amount(),
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
