package com.example.ecommerce.controller;

import com.example.ecommerce.model.entity.Order;
import com.example.ecommerce.model.entity.ShippingInfo;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Order> checkout(@RequestBody ShippingInfo shippingInfo) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        Order order = orderService.checkout(userPrincipal.getId(), shippingInfo);
        return ResponseEntity.ok(order);
    }
}