package com.example.eshop.order.controller;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String memberId,
        @NotNull Long productId,
        @Min(1) int quantity,
        @NotNull BigDecimal totalAmount
) {}
