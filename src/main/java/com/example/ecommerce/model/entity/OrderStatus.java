package com.example.ecommerce.model.entity;

public enum OrderStatus {
    PENDING,      // Initial state when order is created
    PROCESSING,   // Payment confirmed, preparing order
    SHIPPED,      // Order has been shipped
    DELIVERED,    // Order has been delivered
    CANCELLED    // Order was cancelled
}
