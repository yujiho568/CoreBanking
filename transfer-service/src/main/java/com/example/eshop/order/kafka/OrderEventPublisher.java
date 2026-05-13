package com.example.eshop.order.kafka;

import com.example.eshop.common.event.OrderCancelledEvent;
import com.example.eshop.common.event.OrderCompletedEvent;
import com.example.eshop.common.event.OrderCreatedEvent;
import com.example.eshop.common.event.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("publish order.created orderId={}", event.orderId());
        kafkaTemplate.send(Topics.ORDER_CREATED, event.orderId(), event);
    }

    public void publishOrderCompleted(OrderCompletedEvent event) {
        log.info("publish order.completed orderId={}", event.orderId());
        kafkaTemplate.send(Topics.ORDER_COMPLETED, event.orderId(), event);
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.info("publish order.cancelled orderId={}", event.orderId());
        kafkaTemplate.send(Topics.ORDER_CANCELLED, event.orderId(), event);
    }
}
