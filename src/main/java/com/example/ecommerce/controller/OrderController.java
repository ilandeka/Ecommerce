package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.OrderResponse;
import com.example.ecommerce.model.dto.PaymentResponse;
import com.example.ecommerce.model.entity.Order;
import com.example.ecommerce.model.entity.ShippingInfo;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<OrderResponse>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

        // Get the authenticated user
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        // Create pageable request with sorting
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(createSortOrder(sort))
        );

        // Fetch orders
        Page<OrderResponse> orders = orderService.getUserOrders(
                userPrincipal.getId(),
                pageable
        );

        return ResponseEntity.ok(orders);
    }

    // Helper method to create sort order from string parameters
    private List<Sort.Order> createSortOrder(String[] sort) {
        List<Sort.Order> orders = new ArrayList<>();

        if (sort[0].contains(",")) {
            // sort=[field,direction]
            for (String sortOrder : sort) {
                String[] parts = sortOrder.split(",");
                orders.add(new Sort.Order(
                        Sort.Direction.fromString(parts[1]),
                        parts[0]
                ));
            }
        } else {
            // sort=[field] defaults to desc
            orders.add(new Sort.Order(
                    Sort.Direction.DESC,
                    sort[0]
            ));
        }

        return orders;
    }
}