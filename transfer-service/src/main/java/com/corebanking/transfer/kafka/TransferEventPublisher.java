package com.corebanking.transfer.kafka;

import com.corebanking.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTransferCreated(TransferCreatedEvent event) {
        log.info("publish transfer.created transferId={}", event.transferId());
        kafkaTemplate.send(Topics.TRANSFER_CREATED, event.transferId(), event);
    }

    public void publishTransferCompleted(TransferCompletedEvent event) {
        log.info("publish transfer.completed transferId={}", event.transferId());
        kafkaTemplate.send(Topics.TRANSFER_COMPLETED, event.transferId(), event);
    }

    public void publishTransferCancelled(TransferCancelledEvent event) {
        log.info("publish transfer.cancelled transferId={}", event.transferId());
        kafkaTemplate.send(Topics.TRANSFER_CANCELLED, event.transferId(), event);
    }
}
