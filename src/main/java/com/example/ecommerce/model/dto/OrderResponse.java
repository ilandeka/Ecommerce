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
    // Pagination fields
    private List<OrderResponse> content; // For page content
    private int totalPages;
    private long totalElements;
    private int size;
    private int number;

    private Long id;
    private BigDecimal total;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private LocalDateTime createdAt;
    private List<OrderItemDTO> items;
    private ShippingInfoDTO shippingInfo;

    // Inner class for shipping info
    @Getter @Setter
    public static class ShippingInfoDTO {
        private String fullName;
        private String address;
        private String city;
        private String state;
        private String zipCode;
    }

    // Inner class to represent order items
    @Getter @Setter
    public static class OrderItemDTO {
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }
}