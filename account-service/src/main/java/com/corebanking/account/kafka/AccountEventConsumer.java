package com.corebanking.account.kafka;

import com.corebanking.account.service.AccountService;
import com.corebanking.common.event.Topics;
import com.corebanking.common.event.TransferCancelledEvent;
import com.corebanking.common.event.TransferCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = Topics.TRANSFER_CREATED, groupId = "account-service")
    public void onTransferCreated(TransferCreatedEvent event) {
        log.info("consume transfer.created transferId={}", event.transferId());
        accountService.reserveBalance(event);
    }

    @KafkaListener(topics = Topics.TRANSFER_CANCELLED, groupId = "account-service")
    public void onTransferCancelled(TransferCancelledEvent event) {
        log.info("consume transfer.cancelled transferId={}", event.transferId());
        accountService.rollbackBalance(event);
    }
}
