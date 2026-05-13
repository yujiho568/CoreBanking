package com.example.eshop.order.kafka;

import com.example.eshop.common.event.*;
import com.example.eshop.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        log.info("consume inventory.reserved orderId={}", event.orderId());
        orderService.onInventoryReserved(event);
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED, groupId = "order-service")
    public void onInventoryFailed(InventoryFailedEvent event) {
        log.info("consume inventory.failed orderId={}", event.orderId());
        orderService.onInventoryFailed(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED, groupId = "order-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("consume payment.completed orderId={}", event.orderId());
        orderService.onPaymentCompleted(event);
    }

    @KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("consume payment.failed orderId={}", event.orderId());
        orderService.onPaymentFailed(event);
    }
}
