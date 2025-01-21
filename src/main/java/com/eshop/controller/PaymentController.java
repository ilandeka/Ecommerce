package com.eshop.controller;

import com.eshop.model.dto.PaymentRequest;
import com.eshop.model.dto.PaymentResponse;
import com.eshop.model.entity.Order;
import com.eshop.service.OrderService;
import com.eshop.service.PaymentService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final OrderService orderService;

    public PaymentController(PaymentService paymentService, OrderService orderService) {
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    @PostMapping("/create-payment-intent")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentResponse> createPaymentIntent(@RequestBody PaymentRequest request) {
        Order order = orderService.getOrder(request.getOrderId());

        // If payment intent already exists, return it
        try {
            if (order.getPaymentId() != null) {
                PaymentIntent existingIntent = PaymentIntent.retrieve(order.getPaymentId());
                PaymentResponse response = new PaymentResponse();
                response.setClientSecret(existingIntent.getClientSecret());
                response.setPaymentIntentId(existingIntent.getId());
                response.setOrderId(order.getId());
                return ResponseEntity.ok(response);
            }
        } catch (StripeException e) {
            // Create new payment intent if retrieval fails
            return ResponseEntity.ok(paymentService.createPaymentIntent(
                    order.getId(),
                    order.getCurrency(),
                    order.getShippingInfo()
            ));
        }

        // If no payment intent exists, create new one
        return ResponseEntity.ok(paymentService.createPaymentIntent(
                order.getId(),
                order.getCurrency(),
                order.getShippingInfo()
        ));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        paymentService.handleWebhook(payload, sigHeader);
        return ResponseEntity.ok().build();
    }
}