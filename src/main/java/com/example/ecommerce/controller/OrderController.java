package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.CheckoutRequest;
import com.example.ecommerce.model.dto.OrderResponse;
import com.example.ecommerce.model.dto.PaymentResponse;
import com.example.ecommerce.model.entity.Order;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PaymentService;
import com.example.ecommerce.util.SortingUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public ResponseEntity<PaymentResponse> checkout(@RequestBody CheckoutRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        // Create order with shipping info
        Order order = orderService.checkout(userPrincipal.getId(), request.getShippingInfo());

        // Create payment intent with shipping info
        PaymentResponse paymentResponse = paymentService.createPaymentIntent(
                order.getId(),
                order.getCurrency(),
                request.getShippingInfo()
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
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        // Validate the sort field using the utility class
        SortingUtils.validateSortField(sortParams[0], SortingUtils.ALLOWED_ORDER_FIELDS);

        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        // Create sort using the utility class
        Sort sorting = SortingUtils.createSort(sort);
        Pageable pageable = PageRequest.of(page, size, sorting);

        Page<OrderResponse> orders = orderService.getUserOrders(
                userPrincipal.getId(),
                pageable
        );

        return ResponseEntity.ok(orders);
    }
}