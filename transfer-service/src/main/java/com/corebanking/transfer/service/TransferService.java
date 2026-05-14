package com.corebanking.transfer.service;

import com.corebanking.common.event.*;
import com.corebanking.transfer.controller.CreateTransferRequest;
import com.corebanking.transfer.domain.Transfer;
import com.corebanking.transfer.domain.TransferRepository;
import com.corebanking.transfer.kafka.TransferEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferEventPublisher publisher;

    @Transactional
    public Transfer createTransfer(CreateTransferRequest req) {
        String transferId = UUID.randomUUID().toString();
        Transfer transfer = Transfer.create(transferId, req.fromAccountId(), req.toAccountId(), req.amount());
        transfer.markAccountChecking();
        transferRepository.save(transfer);

        publisher.publishTransferCreated(
                TransferCreatedEvent.of(transferId, req.fromAccountId(), req.toAccountId(), req.amount())
        );
        log.info("transfer_created transferId={} from={} to={} amount={}",
                transferId, req.fromAccountId(), req.toAccountId(), req.amount());
        return transfer;
    }

    // account.reserved → LEDGER_PROCESSING 전환
    @Transactional
    public void onAccountReserved(AccountReservedEvent event) {
        transferRepository.findById(event.transferId()).ifPresent(transfer -> {
            transfer.markLedgerProcessing();
            log.info("transfer_ledger_processing transferId={}", event.transferId());
        });
    }

    // account.failed → CANCELLED + transfer.cancelled 발행 (보상 불필요 — 잔액 변경 없음)
    @Transactional
    public void onAccountFailed(AccountFailedEvent event) {
        transferRepository.findById(event.transferId()).ifPresent(transfer -> {
            transfer.cancel(event.reason());
            log.warn("transfer_cancelled_by_account transferId={} reason={}", event.transferId(), event.reason());
            publisher.publishTransferCancelled(
                    TransferCancelledEvent.of(event.transferId(), event.fromAccountId(), null, event.reason())
            );
        });
    }

    // ledger.recorded → COMPLETED + transfer.completed 발행
    @Transactional
    public void onLedgerRecorded(LedgerRecordedEvent event) {
        transferRepository.findById(event.transferId()).ifPresent(transfer -> {
            transfer.complete();
            log.info("transfer_completed transferId={}", event.transferId());
            publisher.publishTransferCompleted(
                    TransferCompletedEvent.of(event.transferId(), transfer.getFromAccountId(),
                            transfer.getToAccountId(), event.amount())
            );
        });
    }

    // ledger.failed → CANCELLED + transfer.cancelled 발행 (보상 트랜잭션 트리거)
    @Transactional
    public void onLedgerFailed(LedgerFailedEvent event) {
        transferRepository.findById(event.transferId()).ifPresent(transfer -> {
            transfer.cancel(event.reason());
            log.warn("transfer_cancelled_by_ledger transferId={} reason={}", event.transferId(), event.reason());
            publisher.publishTransferCancelled(
                    TransferCancelledEvent.of(event.transferId(), transfer.getFromAccountId(),
                            transfer.getAmount(), event.reason())
            );
        });
    }

    @Transactional(readOnly = true)
    public Transfer getTransfer(String transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
    }
}
