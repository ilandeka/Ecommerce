package com.example.ecommerce.service;

import com.example.ecommerce.config.WebhookConfig;
import com.example.ecommerce.model.dto.PaymentResponse;
import com.example.ecommerce.model.entity.*;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public PaymentResponse createPaymentIntent(Long orderId, String currency, ShippingInfo shippingInfo) {
        try {
            logger.info("Creating payment intent for order {} with shipping info: {}", orderId, shippingInfo);

            // Get order
            Order order = orderService.getOrder(orderId);

            // Validate shipping info
            if (shippingInfo == null) {
                logger.error("Shipping info is null for order {}", orderId);
                throw new IllegalArgumentException("Shipping information is required");
            }

            // Create shipping params for Stripe
            PaymentIntentCreateParams.Shipping shipping = PaymentIntentCreateParams.Shipping.builder()
                    .setName(shippingInfo.getFullName())
                    .setAddress(
                            PaymentIntentCreateParams.Shipping.Address.builder()
                                    .setLine1(shippingInfo.getAddress())
                                    .setCity(shippingInfo.getCity())
                                    .setState(shippingInfo.getState())
                                    .setPostalCode(shippingInfo.getZipCode())
                                    .setCountry("US")  // Make this configurable
                                    .build()
                    )
                    .build();

            // Create payment intent with shipping info
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(order.getTotal().multiply(new BigDecimal("100")).longValue()) // Convert to cents
                    .setCurrency(currency.toLowerCase())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("orderId", orderId.toString())
                    .putMetadata("orderItems", formatOrderItems(order.getItems()))
                    .putMetadata("customerName", order.getUser().getFullName())
                    .putMetadata("customerEmail", order.getUser().getEmail())
                    .setShipping(shipping)  // Add shipping info to payment intent
                    .setDescription("Order #" + orderId)  // Add order description
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Store the payment intent ID with the order
            order.setPaymentId(paymentIntent.getId());
            orderService.save(order);

            // Create response
            PaymentResponse response = new PaymentResponse();
            response.setClientSecret(paymentIntent.getClientSecret());
            response.setPaymentIntentId(paymentIntent.getId());

            return response;
        } catch (StripeException e) {
            logger.error("Error creating payment intent for order: {}", orderId, e);
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

            // Verify this is the correct payment intent for this order
            if (!paymentIntent.getId().equals(order.getPaymentId())) {
                logger.error("Payment intent ID mismatch for order: {}", orderId);
                return;
            }

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

    private String formatOrderItems(List<OrderItem> items) {
        return items.stream()
                .map(item -> String.format("%dx %s", item.getQuantity(), item.getProduct().getName()))
                .collect(Collectors.joining(", "));
    }
}