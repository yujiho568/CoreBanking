package com.corebanking.ledger.kafka;

import com.corebanking.common.event.AccountReservedEvent;
import com.corebanking.common.event.Topics;
import com.corebanking.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEventConsumer {

    private final LedgerService ledgerService;

    @KafkaListener(topics = Topics.ACCOUNT_RESERVED, groupId = "ledger-service")
    public void onAccountReserved(AccountReservedEvent event) {
        log.info("consume account.reserved transferId={}", event.transferId());
        ledgerService.recordDoubleEntry(event);
    }
}
