package com.corebanking.account.kafka;

import com.corebanking.common.event.AccountFailedEvent;
import com.corebanking.common.event.AccountReservedEvent;
import com.corebanking.common.event.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAccountReserved(AccountReservedEvent event) {
        log.info("publish account.reserved transferId={}", event.transferId());
        kafkaTemplate.send(Topics.ACCOUNT_RESERVED, event.transferId(), event);
    }

    public void publishAccountFailed(AccountFailedEvent event) {
        log.info("publish account.failed transferId={}", event.transferId());
        kafkaTemplate.send(Topics.ACCOUNT_FAILED, event.transferId(), event);
    }
}
