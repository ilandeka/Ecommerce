package com.example.ecommerce.model.entity;

public enum PaymentStatus {
    PENDING,    // Payment not yet processed
    PAID,       // Payment successful
    FAILED,     // Payment failed
    REFUNDED    // Payment was refunded
}
