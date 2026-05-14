package com.corebanking.transfer.kafka;

import com.corebanking.common.event.*;
import com.corebanking.transfer.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventConsumer {

    private final TransferService transferService;

    @KafkaListener(topics = Topics.ACCOUNT_RESERVED, groupId = "transfer-service")
    public void onAccountReserved(AccountReservedEvent event) {
        log.info("consume account.reserved transferId={}", event.transferId());
        transferService.onAccountReserved(event);
    }

    @KafkaListener(topics = Topics.ACCOUNT_FAILED, groupId = "transfer-service")
    public void onAccountFailed(AccountFailedEvent event) {
        log.info("consume account.failed transferId={}", event.transferId());
        transferService.onAccountFailed(event);
    }

    @KafkaListener(topics = Topics.LEDGER_RECORDED, groupId = "transfer-service")
    public void onLedgerRecorded(LedgerRecordedEvent event) {
        log.info("consume ledger.recorded transferId={}", event.transferId());
        transferService.onLedgerRecorded(event);
    }

    @KafkaListener(topics = Topics.LEDGER_FAILED, groupId = "transfer-service")
    public void onLedgerFailed(LedgerFailedEvent event) {
        log.info("consume ledger.failed transferId={}", event.transferId());
        transferService.onLedgerFailed(event);
    }
}
