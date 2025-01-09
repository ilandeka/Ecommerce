package com.example.ecommerce.controller;

import com.example.ecommerce.model.dto.AddToCartRequest;
import com.example.ecommerce.model.dto.CartResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CartResponse> addToCart(@RequestBody AddToCartRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(cartService.addToCart(userPrincipal.getId(), request));
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CartResponse> getCart() {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(cartService.getCartResponse(userPrincipal.getId()));
    }

    @PutMapping("/items")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CartResponse> updateCartItem(@RequestBody AddToCartRequest request) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(cartService.updateCartItem(userPrincipal.getId(), request));
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CartResponse> removeFromCart(@PathVariable Long productId) {
        UserPrincipal userPrincipal = (UserPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(cartService.removeFromCart(userPrincipal.getId(), productId));
    }
}

