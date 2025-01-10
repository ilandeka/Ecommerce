package com.example.ecommerce.service;

import com.example.ecommerce.model.entity.*;
import com.example.ecommerce.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductService productService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productService = productService;
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public void save(Order order) {
        orderRepository.save(order);
    }

    public Order checkout(Long userId, ShippingInfo shippingInfo) {
        // Get the user's cart
        Cart cart = cartService.getCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        // Create order from cart
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setTotal(cart.getTotal());
        order.setShippingInfo(shippingInfo);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);

        // Convert cart items to order items
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getPrice());
            order.getItems().add(orderItem);

            // Update product stock
            productService.updateStock(cartItem.getProduct().getId(),
                    cartItem.getQuantity());
        }

        // Save the order
        order = orderRepository.save(order);

        // Clear the cart
        cartService.clearCart(userId);

        return order;
    }
}