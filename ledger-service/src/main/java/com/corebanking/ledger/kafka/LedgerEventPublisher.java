package com.corebanking.ledger.kafka;

import com.corebanking.common.event.LedgerFailedEvent;
import com.corebanking.common.event.LedgerRecordedEvent;
import com.corebanking.common.event.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LedgerEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishLedgerRecorded(LedgerRecordedEvent event) {
        log.info("publish ledger.recorded transferId={}", event.transferId());
        kafkaTemplate.send(Topics.LEDGER_RECORDED, event.transferId(), event);
    }

    public void publishLedgerFailed(LedgerFailedEvent event) {
        log.info("publish ledger.failed transferId={}", event.transferId());
        kafkaTemplate.send(Topics.LEDGER_FAILED, event.transferId(), event);
    }
}
