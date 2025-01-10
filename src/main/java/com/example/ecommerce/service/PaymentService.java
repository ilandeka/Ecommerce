package com.example.ecommerce.service;

import com.example.ecommerce.config.WebhookConfig;
import com.example.ecommerce.model.dto.PaymentResponse;
import com.example.ecommerce.model.entity.Order;
import com.example.ecommerce.model.entity.OrderStatus;
import com.example.ecommerce.model.entity.PaymentStatus;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final OrderService orderService;
    private final WebhookConfig webhookConfig;

    public PaymentService(OrderService orderService, WebhookConfig webhookConfig) {
        this.orderService = orderService;
        this.webhookConfig = webhookConfig;
    }

    public PaymentResponse createPaymentIntent(Long orderId, String currency) {
        try {
            // Get order
            Order order = orderService.getOrder(orderId);

            // Create payment intent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(order.getTotal().multiply(new BigDecimal("100")).longValue()) // Convert to cents
                    .setCurrency(currency.toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("orderId", orderId.toString())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Create response
            PaymentResponse response = new PaymentResponse();
            response.setClientSecret(paymentIntent.getClientSecret());
            response.setPaymentIntentId(paymentIntent.getId());

            return response;
        } catch (StripeException e) {
            throw new RuntimeException("Error creating payment intent", e);
        }
    }

    public void handleWebhook(String payload, String sigHeader) {
        try {
            // Verify webhook signature
            Event event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    webhookConfig.getWebhookSecret()
            );

            // Handle different event types
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;

                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;

                case "charge.refunded":
                    handleChargeRefunded(event);
                    break;

                default:
                    logger.info("Unhandled event type: {}", event.getType());
            }
        } catch (SignatureVerificationException e) {
            logger.error("Invalid webhook signature", e);
            throw new RuntimeException("Invalid webhook signature");
        } catch (Exception e) {
            logger.error("Error processing webhook", e);
            throw new RuntimeException("Error processing webhook");
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        // Use proper casting and API methods
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new RuntimeException("Invalid event data"));

        String orderId = paymentIntent.getMetadata().get("orderId");

        try {
            Order order = orderService.getOrder(Long.valueOf(orderId));
            order.setStatus(OrderStatus.PROCESSING);  // Order status changes to processing
            order.setPaymentStatus(PaymentStatus.PAID);  // Payment status changes to paid
            order.setPaymentId(paymentIntent.getId());
            order.setPaidAt(LocalDateTime.now());
            orderService.save(order);

            logger.info("Payment succeeded for order: {}", orderId);
        } catch (Exception e) {
            logger.error("Error processing payment success for order: {}", orderId, e);
        }
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElseThrow(() -> new RuntimeException("Invalid event data"));

        String orderId = paymentIntent.getMetadata().get("orderId");

        try {
            Order order = orderService.getOrder(Long.valueOf(orderId));
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setPaymentId(paymentIntent.getId());
            orderService.save(order);

            logger.warn("Payment failed for order: {}", orderId);
        } catch (Exception e) {
            logger.error("Error processing payment failure for order: {}", orderId, e);
        }
    }

    private void handleChargeRefunded(Event event) {
        try {
            Charge charge = (Charge) event.getDataObjectDeserializer()
                    .getObject().orElseThrow(() -> new RuntimeException("Invalid event data"));

            // Get the PaymentIntent ID from the charge
            String paymentIntentId = charge.getPaymentIntent();

            // Retrieve the full PaymentIntent object
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            // Safely get the orderId from metadata, providing a default if null
            String orderId = Optional.ofNullable(paymentIntent.getMetadata())
                    .map(metadata -> metadata.get("orderId"))
                    .orElseThrow(() -> new RuntimeException("Order ID not found in payment metadata"));

            Order order = orderService.getOrder(Long.valueOf(orderId));
            order.setStatus(OrderStatus.CANCELLED);
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            orderService.save(order);

            logger.info("Refund processed for order: {}", orderId);
        } catch (Exception e) {
            logger.error("Error processing refund", e);
            throw new RuntimeException("Failed to process refund", e);
        }
    }
}