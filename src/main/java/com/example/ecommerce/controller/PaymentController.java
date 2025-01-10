package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.PaymentRequest;
import com.example.ecommerce.model.dto.PaymentResponse;
import com.example.ecommerce.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-payment-intent")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentResponse> createPaymentIntent(
            @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.createPaymentIntent(
                request.getOrderId(),
                request.getCurrency()
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