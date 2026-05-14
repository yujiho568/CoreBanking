package com.corebanking.notification.kafka;

import com.corebanking.common.event.Topics;
import com.corebanking.common.event.TransferCancelledEvent;
import com.corebanking.common.event.TransferCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventConsumer {

    @KafkaListener(topics = Topics.TRANSFER_COMPLETED, groupId = "notification-service")
    public void onTransferCompleted(TransferCompletedEvent event) {
        log.info("[알림] 이체 완료 — transferId={} from={} to={} amount={}",
                event.transferId(), event.fromAccountId(), event.toAccountId(), event.amount());
    }

    @KafkaListener(topics = Topics.TRANSFER_CANCELLED, groupId = "notification-service")
    public void onTransferCancelled(TransferCancelledEvent event) {
        log.warn("[알림] 이체 취소 — transferId={} from={} reason={}",
                event.transferId(), event.fromAccountId(), event.reason());
    }
}
