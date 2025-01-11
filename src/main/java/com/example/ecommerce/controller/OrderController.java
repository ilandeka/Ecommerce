package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.PaymentResponse;
import com.example.ecommerce.model.entity.Order;
import com.example.ecommerce.model.entity.ShippingInfo;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, PaymentService paymentService) {
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentResponse> checkout(@RequestBody ShippingInfo shippingInfo) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        // Create order
        Order order = orderService.checkout(userPrincipal.getId(), shippingInfo);

        // Create payment intent
        PaymentResponse paymentResponse = paymentService.createPaymentIntent(
                order.getId(),
                order.getCurrency()
        );

        // Include the orderId in the response
        paymentResponse.setOrderId(order.getId());

        return ResponseEntity.ok(paymentResponse);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> createOrder() {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        // Create initial order from cart
        Order order = orderService.createOrderFromCart(userPrincipal.getId());
        return ResponseEntity.ok(order);
    }

    @PutMapping("/{orderId}/shipping")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> updateShipping(@PathVariable Long orderId,
                                                @RequestBody ShippingInfo shippingInfo) {
        Order updatedOrder = orderService.updateShippingInfo(orderId, shippingInfo);
        return ResponseEntity.ok(updatedOrder);
    }
}