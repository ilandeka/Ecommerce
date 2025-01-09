package com.example.ecommerce.service;

import com.example.ecommerce.model.entity.Cart;
import com.example.ecommerce.model.entity.CartItem;
import com.example.ecommerce.model.entity.Order;
import com.example.ecommerce.model.entity.OrderItem;
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

    public Order checkout(Long userId) {
        Cart cart = cartService.getCart(userId);
        if (cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setTotal(cart.getTotal());

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

        // Clear the cart
        cartService.clearCart(userId);

        return orderRepository.save(order);
    }
}