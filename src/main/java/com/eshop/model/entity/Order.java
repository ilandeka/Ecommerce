package com.eshop.model.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonManagedReference
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;    // PENDING, PAID, FAILED, REFUNDED

    private String paymentId;          // Stripe Payment Intent ID
    private BigDecimal totalAmount;    // Amount to be paid
    private String currency = "USD";   // Default currency
    private LocalDateTime paidAt;      // When payment was completed

    private LocalDateTime createdAt;

    @Embedded
    private ShippingInfo shippingInfo;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}