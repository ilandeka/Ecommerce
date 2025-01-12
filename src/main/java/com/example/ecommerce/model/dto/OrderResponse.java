package com.example.ecommerce.model.dto;

import com.example.ecommerce.model.entity.OrderStatus;
import com.example.ecommerce.model.entity.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class OrderResponse {
    private Long id;
    private BigDecimal total;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
    private List<OrderItemDTO> items;

    // Inner class to represent order items
    @Getter @Setter
    public static class OrderItemDTO {
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }
}